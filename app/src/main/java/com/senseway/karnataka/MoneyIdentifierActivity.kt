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
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * MoneyIdentifierActivity - DEMO version for INR currency identification
 * FREE: Uses ML Kit Image Labeling as placeholder (can be upgraded to custom TFLite model)
 * 
 * NOTE: This is a DEMO implementation. For production, train a custom TFLite model
 * with INR currency images (₹10, ₹20, ₹50, ₹100, ₹200, ₹500, ₹2000).
 */
class MoneyIdentifierActivity : AppCompatActivity() {
    
    private lateinit var previewView: PreviewView
    private lateinit var tvResult: TextView
    private lateinit var btnCapture: Button
    
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val voiceAssistant = VoiceAssistant(this)
    private var detectedLanguage = "en"
    
    // Demo: Map common labels to currency (this is a placeholder)
    private val currencyKeywords = mapOf(
        "money" to "₹",
        "currency" to "₹",
        "note" to "₹",
        "bill" to "₹"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_money_identifier)
        
        previewView = findViewById(R.id.previewView)
        tvResult = findViewById(R.id.tvResult)
        btnCapture = findViewById(R.id.btnCapture)
        
        tvResult.text = "DEMO MODE: This is a placeholder. Train a custom TFLite model for accurate INR identification."
        
        if (checkCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
        
        btnCapture.setOnClickListener {
            captureAndIdentify()
        }
    }
    
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCameraPermission() {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), 201)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 201 && grantResults.isNotEmpty() && 
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
    
    private fun captureAndIdentify() {
        val imageCapture = imageCapture ?: return
        
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    identifyMoney(imageProxy)
                    imageProxy.close()
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MoneyIdentifierActivity, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    private fun identifyMoney(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            // Use ML Kit Image Labeling (FREE, on-device)
            // NOTE: This is a DEMO. For production, use a custom TFLite model trained on INR currency
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    val result = identifyCurrencyFromLabels(labels)
                    
                    // Display and speak
                    val displayText = if (result != null) {
                        "Detected: $result (DEMO - Use custom TFLite model for accuracy)"
                    } else {
                        "Could not identify currency. Please ensure the note is clearly visible. (DEMO MODE)"
                    }
                    
                    tvResult.text = displayText
                    
                    val speakText = if (result != null) {
                        if (detectedLanguage == "kn") {
                            "ನಾಣ್ಯ: $result"
                        } else {
                            "Currency: $result"
                        }
                    } else {
                        if (detectedLanguage == "kn") {
                            "ನಾಣ್ಯವನ್ನು ಗುರುತಿಸಲಾಗಲಿಲ್ಲ"
                        } else {
                            "Could not identify currency"
                        }
                    }
                    
                    voiceAssistant.speak(speakText, detectedLanguage)
                }
                .addOnFailureListener { e ->
                    val errorMsg = "Failed to analyze: ${e.message}"
                    tvResult.text = errorMsg
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
    
    /**
     * DEMO: Simple heuristic to identify currency from labels
     * PRODUCTION: Replace with custom TFLite model trained on INR currency images
     */
    private fun identifyCurrencyFromLabels(labels: List<com.google.mlkit.vision.label.ImageLabel>): String? {
        // This is a placeholder. In production, use a custom TFLite model.
        // The model should be trained on images of ₹10, ₹20, ₹50, ₹100, ₹200, ₹500, ₹2000 notes.
        
        for (label in labels) {
            val labelText = label.text.lowercase()
            if (currencyKeywords.keys.any { labelText.contains(it) }) {
                // Demo: Return a placeholder. In production, the TFLite model would return the exact denomination.
                return "₹100 (DEMO - Train custom model for accurate identification)"
            }
        }
        
        return null
    }
    
    /**
     * TODO: Integrate custom TFLite model for INR currency identification
     * Steps:
     * 1. Collect training images of ₹10, ₹20, ₹50, ₹100, ₹200, ₹500, ₹2000 notes
     * 2. Train a TensorFlow Lite model
     * 3. Add model to assets/ folder
     * 4. Use TensorFlow Lite Interpreter to run inference
     * 5. Replace identifyCurrencyFromLabels() with TFLite inference
     */
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
