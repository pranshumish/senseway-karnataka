package com.senseway.karnataka

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority

/**
 * WarningZoneManager monitors user location and warns when entering danger zones
 */
class WarningZoneManager private constructor(private val context: Context) {
    
    private val zoneStorage = ZoneStorage(context)
    private val voiceAssistant = VoiceAssistant.getInstance(context)
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    private var locationCallback: LocationCallback? = null
    private var lastWarningTime = mutableMapOf<String, Long>()
    
    // Increased cooldown to 5 minutes (300,000ms) per zone to avoid repetition
    private val WARNING_COOLDOWN_MS = 300000L 
    
    private var isMonitoring = false
    private var isMuted = false
    
    companion object {
        @Volatile
        private var INSTANCE: WarningZoneManager? = null

        fun getInstance(context: Context): WarningZoneManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WarningZoneManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        zoneStorage.initializeDefaultZones()
    }

    /**
     * Mute all danger zone warnings
     */
    fun setMute(muted: Boolean) {
        isMuted = muted
        Log.d("WarningZoneManager", "Warnings muted: $muted")
    }
    
    val isMonitoringActive: Boolean
        get() = isMonitoring

    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    checkWarningZones(location)
                }
            }
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L // Reduced frequency to 10 seconds to save battery
        ).apply {
            setMinUpdateIntervalMillis(5000L)
            setMaxUpdateDelayMillis(20000L)
        }.build()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.d("WarningZoneManager", "Started monitoring warning zones")
        } catch (e: SecurityException) {
            Log.e("WarningZoneManager", "Location permission not granted")
        }
    }
    
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        isMonitoring = false
        Log.d("WarningZoneManager", "Stopped monitoring warning zones")
    }
    
    private fun checkWarningZones(currentLocation: Location) {
        if (isMuted) return

        val zones = zoneStorage.getAllZones()
        
        for (zone in zones) {
            val zoneLocation = Location("").apply {
                latitude = zone.latitude
                longitude = zone.longitude
            }
            
            val distance = currentLocation.distanceTo(zoneLocation)
            
            if (distance <= zone.radiusMeters) {
                val lastWarning = lastWarningTime[zone.id] ?: 0L
                val now = System.currentTimeMillis()
                
                // Only trigger if cooldown period has passed
                if (now - lastWarning > WARNING_COOLDOWN_MS) {
                    triggerWarning(zone, distance.toDouble())
                    lastWarningTime[zone.id] = now
                }
            }
        }
    }
    
    private fun triggerWarning(zone: WarningZone, distance: Double) {
        // Skip warning if assistant is busy (e.g., navigating or responding)
        if (voiceAssistant.isSpeaking()) {
            return
        }

        val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
            }
        }
        
        val warningMessage = "${zone.name}. ${zone.warningText}"
        voiceAssistant.speak(warningMessage, "en", flush = false) // Use QUEUE_ADD via flush=false
    }
    
    fun getCurrentLocation(callback: (Location?) -> Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                callback(location)
            }.addOnFailureListener {
                callback(null)
            }
        } catch (e: SecurityException) {
            callback(null)
        }
    }
}
