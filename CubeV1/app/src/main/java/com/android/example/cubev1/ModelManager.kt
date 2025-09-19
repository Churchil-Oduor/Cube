package com.android.example.cubev1

import android.content.Context
import android.opengl.Matrix
import android.view.Choreographer
import com.google.android.filament.utils.KtxLoader
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer

class ModelManager(
    private val choreographer: Choreographer,
    private val modelViewer: ModelViewer,
    private val context: Context) {

    var aprilMatrix: FloatArray? = null

    val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(currentTime: Long) {
            choreographer.postFrameCallback(this)
            modelViewer.render(currentTime)

        }
    }

    fun loadEnvironment(ibl: String) {
        // Create the indirect light source and add it to the scene.
        var buffer = readAsset("envs/$ibl/${ibl}_ibl.ktx")
        KtxLoader.createIndirectLight(modelViewer.engine, buffer).apply {
            intensity = 50_000f
            modelViewer.scene.indirectLight = this
        }

        // Create the sky box and add it to the scene.
        buffer = readAsset("envs/$ibl/${ibl}_skybox.ktx")
        KtxLoader.createSkybox(modelViewer.engine, buffer).apply {
            modelViewer.scene.skybox = this
        }
    }

    fun loadGlb(name: String, aprilMatrix: FloatArray) {

        val buffer = readAsset("models/${name}.glb")
        modelViewer.loadModelGlb(buffer)
        modelViewer.transformToUnitCube()
        val transformManager = modelViewer.engine.transformManager
        val rootEntity = modelViewer.asset?.root ?: return

//            val instance = transformManager.getInstance(rootEntity)
//            val transformMatrix = FloatArray(16)
//            transformManager.getTransform(instance, transformMatrix)
//
//            val invertedTmatrix = FloatArray(16)
//            Matrix.invertM(invertedTmatrix, 0, transformMatrix, 0)
//
//            val errorMatrix = FloatArray(16)
//            Matrix.multiplyMM(errorMatrix, 0, aprilMatrix, 0, invertedTmatrix, 0)
//
//            val corrected = FloatArray(16)
//            android.opengl.Matrix.multiplyMM(corrected, 0, errorMatrix, 0, transformMatrix, 0)
//            transformManager.setTransform(rootEntity, corrected)
    }

    private fun loadGltf(name: String) {
        val buffer = readAsset("models/${name}.gltf")
        modelViewer.loadModelGltf(buffer) { uri -> readAsset("models/$uri") }
        modelViewer.transformToUnitCube()
    }

        fun readAsset(assetName: String): ByteBuffer {
            val input = context.assets.open(assetName)
            val bytes = ByteArray(input.available())
            input.read(bytes)
            return ByteBuffer.wrap(bytes)
        }



}