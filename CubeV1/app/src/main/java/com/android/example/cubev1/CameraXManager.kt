package com.android.example.streamvideo

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.consumeAsFlow
import java.io.ByteArrayOutputStream
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName


class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: AppCompatActivity,
    private val viewFinder: PreviewView
) {

    var onPoseReceived: ((FloatArray) -> Unit)? = null

    @Serializable
    data class PoseResponse(
        val pose_matrix: List<Float>?
    )

    companion object {
        private val REQUIRED_PERMISSIONS = mutableListOf(
            android.Manifest.permission.CAMERA
        ).toTypedArray()
    }

    private var surfaceProvider: Preview.SurfaceProvider? = null
    private val client = HttpClient(OkHttp) { install(WebSockets) }
    private var webSocketSession: DefaultWebSocketSession? = null
    private var connectionStatus: String = "Disconnected"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastSent = 0L

    fun getConnectionStatus(): String = connectionStatus
    fun setSurfaceProvider(provider: Preview.SurfaceProvider) { surfaceProvider = provider }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }


            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480)) // Reduce size for hotspot bandwidth
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSent >= 100) { // ~10 FPS
                            val byteArray = imageProxy.toJpegByteArray(quality = 80)
                            coroutineScope.launch {
                                try {
                                    webSocketSession?.send(Frame.Binary(true, byteArray))
                                    println("Sent frame of size ${byteArray.size} bytes")
                                } catch (e: Exception) {
                                    println("Failed to send frame: ${e.message}")
                                    connectionStatus = "Error: Failed to send frame: ${e.message}"
                                }
                            }
                            lastSent = currentTime
                        }
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                println("Camera binding failed: ${e.message}")
                connectionStatus = "Error: Camera binding failed: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(context))

        // Start WebSocket connection
        coroutineScope.launch {
            try {
                client.webSocket(
                    method = HttpMethod.Get,
                    host = "10.25.78.137", // 👈 replace with laptop hotspot IP
                    port = 8000,
                    path = "/stream"
                ) {
                    webSocketSession = this
                    connectionStatus = "Connected and streaming"
                    println("WebSocket connected")
                    incoming.consumeAsFlow().collect { frame ->
                        when (frame) {
                            is Frame.Text -> {
                                val jsonReceived = frame.readText()
                                try {
                                    val poseResponse = Json.decodeFromString<PoseResponse>(jsonReceived)
                                    poseResponse.pose_matrix?.let { matrix ->
                                        if (matrix.size == 16) {
                                            val floatArray = matrix.toFloatArray()
                                            onPoseReceived?.invoke(floatArray)
                                            Log.d("Churchil", "Received pose matrix: ${floatArray.joinToString()}")
                                            // 👉 apply correction to your Filament model here
                                        } else {
                                            println("Invalid pose matrix size: ${matrix.size}")
                                        }
                                    } ?: println("No pose matrix in response")
                                } catch (e: Exception) {
                                    println("Failed to parse pose response: ${e.message}")
                                }
                            }
                            else -> {
                                println("Received non-text frame: ${frame.frameType}")
                            }
                        }
                    }

                }
            } catch (e: Exception) {
                connectionStatus = "Error: ${e.message}"
                println("WebSocket connection failed: ${e.message}")
            }
        }
    }



    fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun shutdown() {
        coroutineScope.launch {
            webSocketSession?.close()
            client.close()
        }
        coroutineScope.cancel()
    }

    private val activityResultLauncher =
        lifecycleOwner.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) {
                    permissionGranted = false
                }
            }
            if (!permissionGranted) {
                Toast.makeText(context, "Permission request denied", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }



    // ✅ Convert ImageProxy → NV21 → JPEG safely
    fun ImageProxy.toJpegByteArray(quality: Int = 80): ByteArray {
        val nv21 = yuv420888ToNv21(this)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, out)
        return out.toByteArray()
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4

        val nv21 = ByteArray(ySize + uvSize * 2)

        // Y plane
        val yBuffer = image.planes[0].buffer
        var rowStride = image.planes[0].rowStride
        var pos = 0
        for (row in 0 until height) {
            yBuffer.position(row * rowStride)
            yBuffer.get(nv21, pos, width)
            pos += width
        }

        // UV planes (interleaved VU for NV21)
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        rowStride = image.planes[1].rowStride
        val pixelStride = image.planes[1].pixelStride

        val chromaHeight = height / 2
        val chromaWidth = width / 2

        for (row in 0 until chromaHeight) {
            var uvPos = row * rowStride
            for (col in 0 until chromaWidth) {
                nv21[pos++] = vBuffer.get(uvPos)
                nv21[pos++] = uBuffer.get(uvPos)
                uvPos += pixelStride
            }
        }
        return nv21
    }
}
