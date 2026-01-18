package com.karnataka.senseway

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Locale

class VoiceAssistantService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val commandRouter = CommandRouter()
    private var isListening = false

    override fun onBind(intent: Intent?): IBinder? = null

    // Managers
    private lateinit var dangerManager: DangerZoneManager
    private lateinit var emergencyDispatcher: EmergencyDispatcher
    private lateinit var fallDetector: FallDetector

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(this)
        
        // Initialize Helpers
        emergencyDispatcher = EmergencyDispatcher(this)
        dangerManager = DangerZoneManager(this) { warningMessage ->
            speak(warningMessage)
        }
        
        // Fall Detector
        fallDetector = FallDetector(this) {
             speak("Fall detected! Are you okay? Say I am okay or emergency will be triggered.")
        }
        
        try {
            dangerManager.startTracking()
            fallDetector.start()
        } catch (e: Exception) {
            Log.e("SenseWay", "Error starting sensors: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        // Wait for TTS to initialize before starting listening loop
        return START_STICKY
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, SenseWayApp.CHANNEL_ID)
            .setContentTitle("SenseWay Karnataka Active")
            .setContentText("Listening for commands (English/Kannada)...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(1, notification)
        }
    }

    // --- TextToSpeech.OnInitListener ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
            // Optional: Check if kn-IN is available or default to en-IN
            speak("SenseWay is ready. Listening.")
            startListening()
        } else {
            Log.e("SenseWay", "TTS Initialization failed")
            // Fallback: try to listen anyway
            startListening()
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startListening() {
        if (isListening) return
        
        // Safety Check for permissions
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e("SenseWay", "Microphone permission is missing!")
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN") 
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        // Run on main thread required
        android.os.Handler(mainLooper).post {
            try {
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d("SenseWay", "Started Listening")
            } catch (e: Exception) {
                Log.e("SenseWay", "Start listening failed", e)
                restartListening()
            }
        }
    }

    private fun restartListening() {
        isListening = false
        speechRecognizer?.cancel()
        // Brief delay before restart to prevent tight loops
        android.os.Handler(mainLooper).postDelayed({ startListening() }, 1000)
    }

    // --- RecognitionListener Callbacks ---
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        isListening = false
    }

    override fun onError(error: Int) {
        isListening = false
        // Auto-restart on error ("Always On" behavior)
        Log.e("SenseWay", "Speech Error: $error")
        restartListening()
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val command = matches[0]
            Log.d("SenseWay", "Heard: $command")
            handleCommand(command)
        }
        startListening()
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun handleCommand(text: String) {
        val action = commandRouter.processCommand(text)
        when (action) {
            CommandRouter.Action.GET_TIME -> {
                speak("It is " + java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date()))
            }
            CommandRouter.Action.ADD_DANGER_ZONE -> {
                dangerManager.addCurrentZone()
                speak("Danger zone added at current location.")
            }
            CommandRouter.Action.EMERGENCY_SOS -> {
                speak("Activating Emergency Protocol.")
                // Replace with actual contact number
                emergencyDispatcher.sendEmergencySMS("1234567890", 0.0, 0.0) 
                emergencyDispatcher.makeEmergencyCall("1234567890")
            }
            CommandRouter.Action.DESCRIBE_SCENE -> {
                speak("Opening camera for vision.")
                // In a real implementation, this would launch a transparent Activity to capture the image
                // and pass it to ObjectRecognizer. For now, we launch system camera as a fallback.
                val intent = Intent("android.media.action.IMAGE_CAPTURE")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                     startActivity(intent)
                } catch (e: Exception) {
                     speak("Camera not found or not allowed in background.")
                }
            }
            CommandRouter.Action.STOP_ALARM -> speak("Alarm stopped")
            CommandRouter.Action.I_AM_OKAY -> speak("Okay, emergency cancelled")
            
            CommandRouter.Action.TRANSPORT_MODE -> {
                speak("Opening Transit Maps")
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.apps.maps"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { startActivity(intent) } catch(e: Exception) { speak("Maps not found") }
            }
            
            CommandRouter.Action.NONE -> { /* Ignore non-commands */ }
            else -> { /* No Op */ }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        if (::dangerManager.isInitialized) dangerManager.stopTracking()
        if (::fallDetector.isInitialized) fallDetector.stop()
    }
}
