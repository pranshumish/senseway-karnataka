package com.senseway.karnataka

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * EmergencyManager handles emergency situations.
 * Fixed to ensure siren plays continuously until cancelled.
 */
class EmergencyManager private constructor(private val context: Context) {
    
    private val voiceAssistant = VoiceAssistant.getInstance(context)
    private val warningZoneManager = WarningZoneManager.getInstance(context)
    private val dropDetectionManager = DropDetectionManager(context)
    
    private var mediaPlayer: MediaPlayer? = null
    private var alarmHandler: Handler? = null
    private var isAlarmActive = false
    private val EMERGENCY_TIMEOUT_MS = 7 * 60 * 1000L // 7 minutes
    
    private val emergencyContactStorage = EmergencyContactStorage(context)
    
    companion object {
        @Volatile
        private var INSTANCE: EmergencyManager? = null

        fun getInstance(context: Context): EmergencyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EmergencyManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        dropDetectionManager.startMonitoring {
            triggerEmergency()
        }
    }
    
    fun triggerEmergency() {
        if (isAlarmActive) return
        
        isAlarmActive = true
        Log.d("EmergencyManager", "Emergency triggered")
        
        // Mute location warnings
        warningZoneManager.setMute(true)
        
        // Start siren loop
        startSiren()
        
        // Ask "Are you okay?" - Note: This might pause the siren depending on system focus
        voiceAssistant.speak("Emergency activated. Are you okay? Say stop alarm to cancel.", "en")
        
        alarmHandler = Handler(Looper.getMainLooper())
        alarmHandler?.postDelayed({
            if (isAlarmActive) {
                performEmergencyActions()
            }
        }, EMERGENCY_TIMEOUT_MS)
    }
    
    fun cancelAlarm() {
        if (!isAlarmActive) return
        
        isAlarmActive = false
        stopSiren()
        
        warningZoneManager.setMute(false)
        alarmHandler?.removeCallbacksAndMessages(null)
        Log.d("EmergencyManager", "Emergency cancelled")
    }
    
    private fun startSiren() {
        try {
            stopSiren() // Ensure previous instance is gone
            val ringtoneUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            mediaPlayer = MediaPlayer.create(context, ringtoneUri).apply {
                isLooping = true // Set to loop forever
                setVolume(1.0f, 1.0f)
                start()
            }
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Error playing siren: ${e.message}")
        }
    }
    
    private fun stopSiren() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Error stopping siren")
        }
    }
    
    private fun performEmergencyActions() {
        val contacts = emergencyContactStorage.getEmergencyContacts()
        if (contacts.isEmpty()) return
        
        warningZoneManager.getCurrentLocation { location ->
            val locationLink = if (location != null) {
                "https://www.google.com/maps?q=${location.latitude},${location.longitude}"
            } else {
                "Location unavailable"
            }
            
            val message = "EMERGENCY! I need help. My location: $locationLink"
            
            // Notify all contacts
            contacts.forEach { contact ->
                sendEmergencySMS(contact.phoneNumber, message)
            }
            
            // Call the first contact as primary
            makeEmergencyCall(contacts[0].phoneNumber)
            
            voiceAssistant.speak("Emergency contacts have been notified.", "en")
        }
    }
    
    private fun sendEmergencySMS(phoneNumber: String, message: String) {
        try {
            val smsManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.SmsManager
                ?: SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            Log.e("EmergencyManager", "SMS Error: ${e.message}")
        }
    }
    
    private fun makeEmergencyCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Call Error: ${e.message}")
        }
    }
}
