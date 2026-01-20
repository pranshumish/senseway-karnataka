package com.senseway.karnataka

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class TextReaderActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvText: TextView
    private lateinit var btnReadText: Button
    
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    private var isAnalysisActive = false
    private var voiceAssistant: VoiceAssistant? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_reader)

        previewView = findViewById(R.id.previewView)
        tvText = findViewById(R.id.tvText)
        btnReadText = findViewById(R.id.btnReadText)
        
        voiceAssistant = VoiceAssistant.getInstance(this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        btnReadText.setOnClickListener {
            // Toggle analysis or simple visual feedback trigger
            isAnalysisActive = !isAnalysisActive
            if (isAnalysisActive) {
                btnReadText.text = "Reading..."
                tvText.text = "Scanning for text..."
            } else {
                btnReadText.text = "Read Text"
                tvText.text = "Paused"
                voiceAssistant?.stop()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // ImageAnalyzer for real-time text recognition
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isAnalysisActive) {
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                
                                textRecognizer.process(image)
                                    .addOnSuccessListener { visionText ->
                                        processText(visionText.text)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Text recognition failed", e)
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private var lastSpokenText = ""
    private var lastSpokenTime = 0L

    private fun processText(text: String) {
        if (text.isBlank()) return

        runOnUiThread {
            tvText.text = text
        }

        // Avoid repeating the same text properly
        // Only speak if text is significantly different or enough time has passed
        val currentTime = System.currentTimeMillis()
        if (text != lastSpokenText || (currentTime - lastSpokenTime) > 5000) {
             lastSpokenText = text
             lastSpokenTime = currentTime
             voiceAssistant?.speak(text, "en", flush = true)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "TextReaderActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
