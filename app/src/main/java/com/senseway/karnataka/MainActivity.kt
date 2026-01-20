package com.senseway.karnataka

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/**
 * MainActivity - Main entry point with voice-controlled UI
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var btnStartAssistant: Button
    private lateinit var btnTransportMode: Button
    private lateinit var btnWarningMode: Button
    private lateinit var btnAddDangerZone: Button
    private lateinit var btnEmergency: Button
    private lateinit var btnDescribeScene: Button
    private lateinit var btnIdentifyMoney: Button
    private lateinit var btnEmergencyContact: Button
    private lateinit var btnTextReader: Button
    
    private var isAssistantRunning = false
    private val warningZoneManager: WarningZoneManager by lazy { WarningZoneManager.getInstance(this) }
    private val emergencyManager: EmergencyManager by lazy { EmergencyManager.getInstance(this) }
    private val voiceAssistant: VoiceAssistant by lazy { VoiceAssistant.getInstance(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        setupClickListeners()
        
        // 1. Request standard runtime permissions
        if (!PermissionManager.hasAllRequiredPermissions(this)) {
            PermissionManager.requestPermissions(this)
        } else {
            // Standard permissions granted, now check special overlay permission
            checkSpecialPermissionsAndStart()
        }
    }

    private fun checkSpecialPermissionsAndStart() {
        // 2. Check "Display over other apps" (Required for background Maps launch)
        if (!PermissionManager.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow 'Display over other apps' for navigation to work in background", Toast.LENGTH_LONG).show()
            PermissionManager.requestOverlayPermission(this)
        } else {
            startRequiredServices()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PermissionManager.OVERLAY_PERMISSION_REQUEST_CODE) {
            // User back from overlay settings
            if (PermissionManager.canDrawOverlays(this)) {
                startRequiredServices()
            } else {
                Toast.makeText(this, "Background navigation may not work without overlay permission", Toast.LENGTH_SHORT).show()
                startRequiredServices() // Start anyway, but with reduced functionality
            }
        }
    }
    
    private fun startRequiredServices() {
        // Start warning zone monitoring
        if (PermissionManager.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            warningZoneManager.startMonitoring()
        }
        
        // Start Voice Assistant automatically on launch
        if (PermissionManager.hasPermission(this, Manifest.permission.RECORD_AUDIO)) {
            startVoiceAssistant()
        }
    }
    
    private fun initializeViews() {
        btnStartAssistant = findViewById(R.id.btnStartAssistant)
        btnTransportMode = findViewById(R.id.btnTransportMode)
        btnWarningMode = findViewById(R.id.btnWarningMode)
        btnAddDangerZone = findViewById(R.id.btnAddDangerZone)
        btnEmergency = findViewById(R.id.btnEmergency)
        btnDescribeScene = findViewById(R.id.btnDescribeScene)
        btnIdentifyMoney = findViewById(R.id.btnIdentifyMoney)

        btnEmergencyContact = findViewById(R.id.btnEmergencyContact)
        btnTextReader = findViewById(R.id.btnTextReader)
    }
    
    private fun setupClickListeners() {
        btnStartAssistant.setOnClickListener {
            toggleVoiceAssistant()
        }
        
        btnTransportMode.setOnClickListener {
            voiceAssistant.speak("Opening Transport Mode")
            openTransportMode()
        }
        
        btnWarningMode.setOnClickListener {
            // Toggle logic handles speaking
            toggleWarningMode()
        }
        
        btnAddDangerZone.setOnClickListener {
            voiceAssistant.speak("Opening Add Danger Zone")
            openAddZoneActivity()
        }
        
        btnEmergency.setOnClickListener {
            voiceAssistant.speak("Triggering Emergency Alert", flush = true)
            triggerEmergency()
        }
        
        btnDescribeScene.setOnClickListener {
            voiceAssistant.speak("Opening Scene Description")
            openSceneDescription()
        }
        
        btnIdentifyMoney.setOnClickListener {
            voiceAssistant.speak("Opening Money Identifier")
            openMoneyIdentifier()
        }
        
        btnEmergencyContact.setOnClickListener {
            voiceAssistant.speak("Opening Emergency Contacts")
            openEmergencyContact()
        }
        
        btnTextReader.setOnClickListener {
            voiceAssistant.speak("Opening Text Reader")
            startActivity(Intent(this, TextReaderActivity::class.java))
        }
    }
    
    private fun toggleVoiceAssistant() {
        if (!PermissionManager.hasPermission(this, Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            PermissionManager.requestPermissions(this)
            return
        }
        
        if (!isAssistantRunning) {
            startVoiceAssistant()
            Toast.makeText(this, "Voice assistant started", Toast.LENGTH_SHORT).show()
            voiceAssistant.speak("Voice Assistant Started")
        } else {
            stopVoiceAssistant()
            Toast.makeText(this, "Voice assistant stopped", Toast.LENGTH_SHORT).show()
            voiceAssistant.speak("Voice Assistant Stopped")
        }
    }
    
    private fun startVoiceAssistant() {
        val intent = Intent(this, VoiceService::class.java).apply {
            action = VoiceService.ACTION_START_LISTENING
            putExtra(VoiceService.EXTRA_LANGUAGE, "en-IN") // Default language
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        isAssistantRunning = true
        btnStartAssistant.text = getString(R.string.stop_assistant)
    }
    
    private fun stopVoiceAssistant() {
        val intent = Intent(this, VoiceService::class.java).apply {
            action = VoiceService.ACTION_STOP_LISTENING
        }
        startService(intent)
        
        isAssistantRunning = false
        btnStartAssistant.text = getString(R.string.start_assistant)
    }
    
    private fun openTransportMode() {
        val transportHelper = TransportHelper(this)
        transportHelper.openTransportMode()
    }
    
    private fun toggleWarningMode() {
        if (PermissionManager.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (warningZoneManager.isMonitoringActive) {
                warningZoneManager.stopMonitoring()
                Toast.makeText(this, "Warning mode stopped", Toast.LENGTH_SHORT).show()
                voiceAssistant.speak("Warning Mode Stopped")
            } else {
                warningZoneManager.startMonitoring()
                Toast.makeText(this, "Warning mode activated", Toast.LENGTH_SHORT).show()
                voiceAssistant.speak("Warning Mode Activated")
            }
        } else {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            PermissionManager.requestPermissions(this)
        }
    }
    
    private fun openAddZoneActivity() {
        if (PermissionManager.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            startActivity(Intent(this, AddZoneActivity::class.java))
        } else {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            PermissionManager.requestPermissions(this)
        }
    }
    
    private fun triggerEmergency() {
        emergencyManager.triggerEmergency()
    }
    
    private fun openSceneDescription() {
        if (PermissionManager.hasPermission(this, Manifest.permission.CAMERA)) {
            startActivity(Intent(this, SceneDescriptionActivity::class.java))
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            PermissionManager.requestPermissions(this)
        }
    }
    
    private fun openMoneyIdentifier() {
        if (PermissionManager.hasPermission(this, Manifest.permission.CAMERA)) {
            startActivity(Intent(this, MoneyIdentifierActivity::class.java))
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            PermissionManager.requestPermissions(this)
        }
    }
    
    private fun openEmergencyContact() {
        startActivity(Intent(this, EmergencyContactActivity::class.java))
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PermissionManager.PERMISSION_REQUEST_CODE) {
            if (PermissionManager.hasAllRequiredPermissions(this)) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
                checkSpecialPermissionsAndStart()
            } else {
                Toast.makeText(this, "Some permissions are required for full functionality", Toast.LENGTH_LONG).show()
                checkSpecialPermissionsAndStart()
            }
        }
    }
}
