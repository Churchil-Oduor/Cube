package com.android.example.cubev1

import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Choreographer
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.android.example.cubev1.databinding.ActivityMainBinding
import com.android.example.streamvideo.CameraXManager
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraXManager: CameraXManager
    private lateinit var my_modelManager: ModelManager
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var surfaceView: SurfaceView
    private lateinit var choreographer: Choreographer
    private lateinit var modelViewer: ModelViewer
    private lateinit var preview: PreviewView


    companion object {
        init {
            Utils.init()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        cameraXManager = CameraXManager(this, this, viewBinding.viewFinder)


        surfaceView = findViewById(R.id.surface_view)
        preview = findViewById(R.id.viewFinder)

        choreographer = Choreographer.getInstance()

        modelViewer = ModelViewer(surfaceView).apply {
            scene.skybox = null
            view.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
        }

        surfaceView.setOnTouchListener(modelViewer)
        surfaceView.setZOrderOnTop(true) // Forces it above the camera SurfaceView
        surfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Start camera
        if (cameraXManager.allPermissionsGranted()) {
            cameraXManager.startCamera()
        } else {
            cameraXManager.requestPermissions()
        }
        my_modelManager = ModelManager(choreographer, modelViewer, this)

        cameraXManager.onPoseReceived = {
            matrix ->
            my_modelManager.loadGlb("DamagedHelmet", matrix)
            my_modelManager.loadEnvironment("venetian_crossroads_2k")
            modelViewer.scene.skybox = null
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraXManager.shutdown()
        cameraExecutor.shutdown()
        choreographer.removeFrameCallback(my_modelManager.frameCallback)
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(my_modelManager.frameCallback)
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(my_modelManager.frameCallback)
    }
}
