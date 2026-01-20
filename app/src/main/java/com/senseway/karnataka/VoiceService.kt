package com.senseway.karnataka

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.LifecycleService
import java.util.Locale

/**
 * VoiceService is a Foreground Service that continuously listens for voice commands.
 */
class VoiceService : LifecycleService() {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var commandProcessor: CommandProcessor? = null
    private val voiceAssistant: VoiceAssistant by lazy { VoiceAssistant.getInstance(this) }
    
    private var isListening = false
    private var currentLanguage = "en-IN"
    private var isRecognitionActive = false
    
    private val handler = Handler(Looper.getMainLooper())
    
    // Watchdog to restart listening if it hangs or stops unexpectedly
    private val watchdog = object : Runnable {
        override fun run() {
            if (isListening && !isRecognitionActive && !voiceAssistant.isSpeaking()) {
                Log.d("VoiceService", "Watchdog: Recognizer idle, restarting...")
                startListening(currentLanguage)
            }
            handler.postDelayed(this, 5000) 
        }
    }
    
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): VoiceService = this@VoiceService
    }
    
    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createNotificationChannel(this)
        commandProcessor = CommandProcessor(this)
        initializeRecognizer()
        handler.postDelayed(watchdog, 5000)
    }

    private fun initializeRecognizer() {
        try {
            speechRecognizer?.destroy()
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                setupRecognitionListener()
            }
        } catch (e: Exception) {
            Log.e("VoiceService", "Error initializing recognizer: ${e.message}")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NotificationUtils.NOTIFICATION_ID, 
                NotificationUtils.createNotification(this),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(
                NotificationUtils.NOTIFICATION_ID, 
                NotificationUtils.createNotification(this)
            )
        }
        
        when (intent?.action) {
            ACTION_START_LISTENING -> {
                isListening = true
                currentLanguage = intent.getStringExtra(EXTRA_LANGUAGE) ?: "en-IN"
                startListening(currentLanguage)
            }
            ACTION_STOP_LISTENING -> {
                isListening = false
                stopListening()
                stopSelf()
            }
            ACTION_TOGGLE_LANGUAGE -> {
                toggleLanguage()
            }
            else -> {
                isListening = true
                startListening(currentLanguage)
            }
        }
        return START_STICKY 
    }
    
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }
    
    private fun startListening(language: String) {
        if (isRecognitionActive) return
        
        // Don't listen while the app is talking to avoid hearing itself
        if (voiceAssistant.isSpeaking()) {
            handler.postDelayed({ startListening(language) }, 1000)
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            speechRecognizer?.startListening(intent)
            isRecognitionActive = true
        } catch (e: Exception) {
            isRecognitionActive = false
            initializeRecognizer()
        }
    }
    
    private fun stopListening() {
        isRecognitionActive = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {}
    }
    
    private fun toggleLanguage() {
        currentLanguage = if (currentLanguage.startsWith("en")) "kn-IN" else "en-IN"
        if (isListening) {
            stopListening()
            startListening(currentLanguage)
        }
    }
    
    private fun setupRecognitionListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isRecognitionActive = true
            }
            
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                isRecognitionActive = false
            }
            
            override fun onError(error: Int) {
                isRecognitionActive = false
                
                // Automatically restart listening after error, but wait a bit
                if (isListening) {
                    handler.postDelayed({ if (isListening) startListening(currentLanguage) }, 1500)
                }
            }
            
            override fun onResults(results: Bundle?) {
                isRecognitionActive = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0]
                    val lang = if (currentLanguage.startsWith("kn")) "kn" else "en"
                    commandProcessor?.processCommand(command, lang)
                }
                
                // Wait 2 seconds before listening again to give the app time to speak
                if (isListening) {
                    handler.postDelayed({ if (isListening) startListening(currentLanguage) }, 2000)
                }
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0].lowercase()
                    if (text.contains("stop alarm") || text.contains("i am okay") || text.contains("nillisu")) {
                        val lang = if (currentLanguage.startsWith("kn")) "kn" else "en"
                        commandProcessor?.processCommand(text, lang)
                    }
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        handler.removeCallbacks(watchdog)
        stopListening()
        speechRecognizer?.destroy()
    }
    
    companion object {
        const val ACTION_START_LISTENING = "com.senseway.karnataka.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.senseway.karnataka.STOP_LISTENING"
        const val ACTION_TOGGLE_LANGUAGE = "com.senseway.karnataka.TOGGLE_LANGUAGE"
        const val EXTRA_LANGUAGE = "extra_language"
    }
}
