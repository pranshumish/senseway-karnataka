package com.senseway.karnataka

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class SceneDescriptionActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvDescription: TextView
    private lateinit var btnCapture: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var isListening = false

    private var imageCapture: ImageCapture? = null
    private var isCameraReady = false
    private var voiceAssistant: VoiceAssistant? = null
    private var detectedLanguage = "en"
    
    // Use Gemini for description
    private val geminiPro = GeminiPro()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        try {
            Log.d(TAG, "Setting content view...")
            // Wrap setContentView to handle potential inflation errors
            try {
                setContentView(R.layout.activity_scene_description)
                Log.d(TAG, "Content view set successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "Error setting content view", e)
                Toast.makeText(this, "Layout Error: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            Log.d(TAG, "Finding views...")
            previewView = findViewById(R.id.previewView)
            tvDescription = findViewById(R.id.tvDescription)
            btnCapture = findViewById(R.id.btnCapture)
            Log.d(TAG, "Views found successfully")

            // Initialize VoiceAssistant safely
            try {
                Log.d(TAG, "Initializing VoiceAssistant...")
                voiceAssistant = VoiceAssistant.getInstance(this)
                Log.d(TAG, "VoiceAssistant initialized")
            } catch (e: Throwable) {
                Log.e(TAG, "Error initializing VoiceAssistant", e)
                // Continue without TTS
            }

            // Disable button until camera is ready
            btnCapture.isEnabled = false
            tvDescription.text = "Initializing camera..."

            Log.d(TAG, "Checking camera permission...")
            if (checkCameraPermission()) {
                Log.d(TAG, "Camera permission granted, starting camera...")
                startCamera()
            } else {
                Log.d(TAG, "Camera permission not granted, requesting...")
                requestCameraPermission()
            }

            btnCapture.setOnClickListener {
                if (isCameraReady && imageCapture != null) {
                    captureAndAnalyze()
                } else {
                    Toast.makeText(this, "Camera not ready yet. Please wait...", Toast.LENGTH_SHORT).show()
                }
            }
            
            Log.d(TAG, "onCreate completed successfully")

            // Initialize Speech Recognition
            initializeSpeechRecognizer()

        } catch (e: Throwable) {
            Log.e(TAG, "Fatal error in onCreate", e)
            e.printStackTrace()
            try {
                Toast.makeText(this, "Fatal error: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (toastError: Throwable) {
                Log.e(TAG, "Even Toast failed", toastError)
            }
            // Safely finish to avoid crash loop
            finish()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 200)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
            startListening() // Start listening after permission granted
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing, skipping camera start")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            if (isFinishing || isDestroyed) {
                Log.w(TAG, "Activity finished during camera initialization")
                return@addListener
            }

            try {
                val cameraProvider = cameraProviderFuture.get()

                // Set implementation mode for PreviewView (required for some devices)
                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                
                // Camera is ready
                isCameraReady = true
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        btnCapture.isEnabled = true
                        tvDescription.text = "Tap Capture to analyze scene"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization error", e)
                e.printStackTrace()
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        tvDescription.text = "Camera error: ${e.message}"
                        Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndAnalyze() {
        val imageCapture = this.imageCapture
        if (imageCapture == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }

        btnCapture.isEnabled = false
        tvDescription.text = "Capturing image..."

        try {
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        runOnUiThread {
                            tvDescription.text = "Analyzing scene with Gemini..."
                        }
                        analyzeImage(imageProxy)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Capture failed", exception)
                        runOnUiThread {
                            val errorMsg = "Capture failed: ${exception.message}"
                            tvDescription.text = errorMsg
                            Toast.makeText(
                                this@SceneDescriptionActivity,
                                errorMsg,
                                Toast.LENGTH_SHORT
                            ).show()
                            btnCapture.isEnabled = true
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error taking picture", e)
            runOnUiThread {
                tvDescription.text = "Error: ${e.message}"
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnCapture.isEnabled = true
            }
        }
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        try {
            // Convert ImageProxy to Bitmap
            val bitmap = imageProxy.convertProxyToBitmap()
            imageProxy.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to convert ImageProxy to Bitmap")
                runOnUiThread {
                    tvDescription.text = "Failed to process image"
                    Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
                    btnCapture.isEnabled = true
                }
                return
            }

            // Use Gemini API for image analysis
            lifecycleScope.launch {
                try {
                    val prompt = "Describe what you see in this image in detail for a visually impaired person. Be specific about objects, people, colors, and the scene. Keep it concise but descriptive."
                    val response = geminiPro.getResponse(bitmap, prompt)

                    runOnUiThread {
                        if (response.error != null) {
                            val errorMsg = "Gemini Error: ${response.error}"
                            Log.e(TAG, errorMsg)
                            tvDescription.text = errorMsg
                            voiceAssistant?.speak(errorMsg, detectedLanguage)
                        } else if (response.text != null) {
                            tvDescription.text = response.text
                            voiceAssistant?.speak(response.text, detectedLanguage)
                        } else {
                            val errorMsg = "No response from Gemini"
                            tvDescription.text = errorMsg
                            voiceAssistant?.speak(errorMsg, detectedLanguage)
                        }
                        btnCapture.isEnabled = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling Gemini API", e)
                    runOnUiThread {
                        val errorMsg = "Error: ${e.message ?: "Unknown error"}"
                        tvDescription.text = errorMsg
                        Toast.makeText(this@SceneDescriptionActivity, errorMsg, Toast.LENGTH_SHORT).show()
                        btnCapture.isEnabled = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            imageProxy.close()
            runOnUiThread {
                val errorMsg = "Error: ${e.message ?: "Unknown error"}"
                tvDescription.text = errorMsg
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                btnCapture.isEnabled = true
            }
        }
    }

    private fun ImageProxy.convertProxyToBitmap(): Bitmap? {
        try {
            if (format == ImageFormat.JPEG) {
                val buffer = planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e)
            return null
        }
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    // Restart listening if not critical error
                    if (error != SpeechRecognizer.ERROR_CLIENT && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                         // Add delay to prevent crash loops
                         android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                             if (!isFinishing && !isDestroyed) {
                                 startListening()
                             }
                         }, 1000)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val command = matches[0].lowercase()
                        Log.d(TAG, "Voice command received: $command")
                        
                        if (command.contains("capture") || command.contains("describe") || command.contains("scan") || command.contains("scene") || command.contains("tell me")) {
                             if (isCameraReady && btnCapture.isEnabled) {
                                 voiceAssistant?.speak("Capturing...", detectedLanguage)
                                 captureAndAnalyze()
                             }
                        }
                    }
                    startListening() // Continue listening
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
            // Start listening automatically
            startListening()
        }
    }

    private fun startListening() {
        if (!isListening && speechRecognizer != null) {
            try {
                runOnUiThread {
                     speechRecognizer?.startListening(speechIntent)
                     isListening = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognizer", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    companion object {
        private const val TAG = "SceneDescription"
    }
}
