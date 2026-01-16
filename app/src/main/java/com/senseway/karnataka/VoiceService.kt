package com.senseway.karnataka

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.LifecycleService
import java.util.Locale

/**
 * VoiceService - Foreground Service for always-on voice recognition
 * FREE: Uses Android's built-in SpeechRecognizer (no paid APIs)
 * Supports Kannada (kn-IN) and English (en-IN)
 */
class VoiceService : LifecycleService() {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var commandProcessor: CommandProcessor? = null
    private var isListening = false
    private var currentLanguage = "en" // Default to English
    
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): VoiceService = this@VoiceService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("VoiceService", "Service created")
        
        NotificationUtils.createNotificationChannel(this)
        commandProcessor = CommandProcessor(this)
        
        // Initialize speech recognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            setupRecognitionListener()
        } else {
            Log.e("VoiceService", "Speech recognition not available")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        // Start as foreground service
        startForeground(NotificationUtils.NOTIFICATION_ID, NotificationUtils.createNotification(this))
        
        when (intent?.action) {
            ACTION_START_LISTENING -> {
                startListening()
            }
            ACTION_STOP_LISTENING -> {
                stopListening()
            }
            ACTION_TOGGLE_LANGUAGE -> {
                toggleLanguage()
            }
            else -> {
                // Default: start listening if no action specified
                startListening()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }
    
    fun startListening() {
        if (isListening) return
        
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            setupRecognitionListener()
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLanguageCode())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Support both Kannada and English
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "kn-IN,en-IN")
        }
        
        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            Log.d("VoiceService", "Started listening (language: $currentLanguage)")
        } catch (e: Exception) {
            Log.e("VoiceService", "Error starting recognition: ${e.message}")
        }
    }
    
    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        Log.d("VoiceService", "Stopped listening")
    }
    
    private fun toggleLanguage() {
        currentLanguage = if (currentLanguage == "en") "kn" else "en"
        Log.d("VoiceService", "Language toggled to: $currentLanguage")
        if (isListening) {
            stopListening()
            startListening()
        }
    }
    
    private fun getLanguageCode(): String {
        return when (currentLanguage) {
            "kn" -> "kn-IN"
            "en" -> "en-IN"
            else -> "en-IN"
        }
    }
    
    private fun setupRecognitionListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                Log.d("VoiceService", "Ready for speech")
            }
            
            override fun onBeginningOfSpeech() {
                Log.d("VoiceService", "Beginning of speech")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // Audio level feedback (optional)
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // Not used
            }
            
            override fun onEndOfSpeech() {
                Log.d("VoiceService", "End of speech")
            }
            
            override fun onError(error: Int) {
                Log.e("VoiceService", "Recognition error: $error")
                
                // Auto-restart on certain errors
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Restart listening after a short delay
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (isListening) {
                                startListening()
                            }
                        }, 1000)
                    }
                    SpeechRecognizer.ERROR_AUDIO,
                    SpeechRecognizer.ERROR_CLIENT -> {
                        // Restart after longer delay
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (isListening) {
                                startListening()
                            }
                        }, 2000)
                    }
                }
            }
            
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val command = matches[0]
                    Log.d("VoiceService", "Recognized: $command")
                    
                    // Detect language from result (simple heuristic)
                    val detectedLang = detectLanguage(command)
                    if (detectedLang != currentLanguage) {
                        currentLanguage = detectedLang
                    }
                    
                    // Process command
                    commandProcessor?.processCommand(command, currentLanguage)
                }
                
                // Continue listening
                if (isListening) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startListening()
                    }, 500)
                }
            }
            
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                // Optional: handle partial results
            }
            
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {
                // Not used
            }
        })
    }
    
    /**
     * Simple language detection based on common Kannada words
     */
    private fun detectLanguage(text: String): String {
        val kannadaWords = listOf(
            "ನಾನು", "ನೀವು", "ಅವರು", "ಇದು", "ಅದು", "ಎಲ್ಲಿ", "ಯಾವಾಗ", "ಹೇಗೆ",
            "ಬಸ್", "ಮೆಟ್ರೊ", "ಮಾರ್ಗ", "ಎಚ್ಚರಿಕೆ", "ಅಪಾಯ", "ಅನಾಹುತ", "ವಿವರಿಸಿ"
        )
        
        val lowerText = text.lowercase()
        for (word in kannadaWords) {
            if (lowerText.contains(word)) {
                return "kn"
            }
        }
        return "en"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        commandProcessor?.shutdown()
        commandProcessor = null
        Log.d("VoiceService", "Service destroyed")
    }
    
    companion object {
        const val ACTION_START_LISTENING = "com.senseway.karnataka.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.senseway.karnataka.STOP_LISTENING"
        const val ACTION_TOGGLE_LANGUAGE = "com.senseway.karnataka.TOGGLE_LANGUAGE"
    }
}
