package com.karnataka.senseway

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button

    // Define all required permissions based on Android version
    private val requiredPermissions by lazy {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        perms.toTypedArray()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all are granted
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startVoiceService()
        } else {
            statusText.text = "Permissions Missing.\nApp cannot function without Mic, Location & SMS."
            actionButton.text = "Grant Permissions"
            actionButton.setOnClickListener { checkAndRequestPermissions() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        actionButton = findViewById(R.id.actionButton)

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            // All good, setup UI for Service control
            setupServiceUI()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun setupServiceUI() {
        statusText.text = "SenseWay is Ready.\nTap to Start Assistant."
        actionButton.text = "Start Service"
        actionButton.setOnClickListener {
            startVoiceService()
        }
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = "SenseWay Active.\nListening for commands..."
        actionButton.text = "Stop Service"
        actionButton.setOnClickListener {
            stopService(intent)
            setupServiceUI()
        }
    }
}
