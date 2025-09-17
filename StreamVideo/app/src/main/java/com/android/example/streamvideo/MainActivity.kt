package com.android.example.streamvideo

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraXManager: CameraXManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        cameraXManager = CameraXManager(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (cameraXManager.allPermissionsGranted()) {
            cameraXManager.startCamera()
        } else {
            cameraXManager.requestPermissions()
        }

        setContent {
            CameraPreview(cameraXManager)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraXManager.shutdown()
        cameraExecutor.shutdown()
    }
}

@Composable
fun CameraPreview(cameraXManager: CameraXManager) {
    var connectionStatus by remember { mutableStateOf(cameraXManager.getConnectionStatus()) }

    // Update status when it changes
    LaunchedEffect(Unit) {
        while (true) {
            connectionStatus = cameraXManager.getConnectionStatus()
            delay(1000) // Update every second
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Status: $connectionStatus", modifier = Modifier.padding(16.dp))
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val previewView = PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                cameraXManager.setSurfaceProvider(previewView.surfaceProvider)
                previewView
            }
        )
    }
}