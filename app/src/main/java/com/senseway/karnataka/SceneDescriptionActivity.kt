package com.senseway.karnataka

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * SceneDescriptionActivity uses Google ML Kit (FREE, on-device) to describe scenes
 * Supports Kannada and English output
 */
class SceneDescriptionActivity : AppCompatActivity() {
    
    private lateinit var previewView: PreviewView
    private lateinit var tvDescription: TextView
    private lateinit var btnCapture: Button
    
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val voiceAssistant = VoiceAssistant(this)
    private var detectedLanguage = "en"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scene_description)
        
        previewView = findViewById(R.id.previewView)
        tvDescription = findViewById(R.id.tvDescription)
        btnCapture = findViewById(R.id.btnCapture)
        
        if (checkCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
        
        btnCapture.setOnClickListener {
            captureAndAnalyze()
        }
    }
    
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCameraPermission() {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), 200)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && 
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            imageCapture = ImageCapture.Builder().build()
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun captureAndAnalyze() {
        val imageCapture = imageCapture ?: return
        
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    analyzeImage(imageProxy)
                    imageProxy.close()
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@SceneDescriptionActivity, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    private fun analyzeImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            // Use ML Kit Object Detection (FREE, on-device)
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
            
            val objectDetector = ObjectDetection.getClient(options)
            
            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    val descriptions = mutableListOf<String>()
                    
                    for (detectedObject in detectedObjects) {
                        val label = detectedObject.labels.firstOrNull()
                        if (label != null) {
                            descriptions.add(label.text)
                        }
                    }
                    
                    val description = if (descriptions.isNotEmpty()) {
                        "I can see: ${descriptions.joinToString(", ")}"
                    } else {
                        "No objects detected clearly. Please try again."
                    }
                    
                    // Display and speak
                    tvDescription.text = description
                    voiceAssistant.speak(description, detectedLanguage)
                }
                .addOnFailureListener { e ->
                    val errorMsg = "Failed to analyze image: ${e.message}"
                    tvDescription.text = errorMsg
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
