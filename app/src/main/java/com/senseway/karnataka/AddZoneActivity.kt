package com.senseway.karnataka

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

/**
 * AddZoneActivity allows users to add or clear danger zones
 */
class AddZoneActivity : AppCompatActivity() {
    
    private var etZoneName: EditText? = null
    private var etWarningText: EditText? = null
    private var etRadius: EditText? = null
    private var btnSaveZone: Button? = null
    private var btnClearZones: Button? = null
    
    private val zoneStorage: ZoneStorage by lazy { ZoneStorage(applicationContext) }
    private val warningZoneManager: WarningZoneManager by lazy { WarningZoneManager.getInstance(applicationContext) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_zone)
        
        etZoneName = findViewById(R.id.etZoneName)
        etWarningText = findViewById(R.id.etWarningText)
        etRadius = findViewById(R.id.etRadius)
        btnSaveZone = findViewById(R.id.btnSaveZone)
        btnClearZones = findViewById(R.id.btnClearZones)
        
        etRadius?.setText("50")
        
        btnSaveZone?.setOnClickListener {
            saveZone()
        }

        btnClearZones?.setOnClickListener {
            zoneStorage.clearAllZones()
            Toast.makeText(this, "All zones deleted", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveZone() {
        val name = etZoneName?.text?.toString()?.trim() ?: ""
        val warningText = etWarningText?.text?.toString()?.trim() ?: ""
        val radiusText = etRadius?.text?.toString()?.trim() ?: ""
        
        if (name.isEmpty() || warningText.isEmpty() || radiusText.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        
        val radius = radiusText.toDoubleOrNull() ?: 50.0
        
        warningZoneManager.getCurrentLocation { location ->
            if (location == null) {
                Toast.makeText(this, "Location error", Toast.LENGTH_SHORT).show()
                return@getCurrentLocation
            }
            
            val zone = WarningZone(
                id = UUID.randomUUID().toString(),
                name = name,
                warningText = warningText,
                latitude = location.latitude,
                longitude = location.longitude,
                radiusMeters = radius
            )
            
            if (zoneStorage.saveZone(zone)) {
                Toast.makeText(this, "Saved: $name", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
