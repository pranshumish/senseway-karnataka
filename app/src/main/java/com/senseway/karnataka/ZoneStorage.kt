package com.senseway.karnataka

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray

/**
 * ZoneStorage manages warning zones using SharedPreferences
 */
class ZoneStorage(private val context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("warning_zones", Context.MODE_PRIVATE)
    private val KEY_ZONES = "zones"
    
    fun saveZone(zone: WarningZone): Boolean {
        return try {
            val zones = getAllZones().toMutableList()
            zones.add(zone)
            saveZones(zones)
            true
        } catch (e: Exception) {
            Log.e("ZoneStorage", "Error saving zone: ${e.message}")
            false
        }
    }
    
    fun getAllZones(): List<WarningZone> {
        val zonesJson = prefs.getString(KEY_ZONES, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(zonesJson)
            val zones = mutableListOf<WarningZone>()
            for (i in 0 until jsonArray.length()) {
                val zone = WarningZone.fromJsonString(jsonArray.getString(i))
                if (zone != null) {
                    zones.add(zone)
                }
            }
            zones
        } catch (e: Exception) {
            Log.e("ZoneStorage", "Error loading zones: ${e.message}")
            emptyList()
        }
    }
    
    fun deleteZone(zoneId: String): Boolean {
        return try {
            val zones = getAllZones().filter { it.id != zoneId }
            saveZones(zones)
            true
        } catch (e: Exception) {
            Log.e("ZoneStorage", "Error deleting zone: ${e.message}")
            false
        }
    }

    /**
     * Clear all saved zones
     */
    fun clearAllZones() {
        prefs.edit().remove(KEY_ZONES).apply()
        Log.d("ZoneStorage", "All zones cleared")
    }
    
    private fun saveZones(zones: List<WarningZone>) {
        val jsonArray = JSONArray()
        zones.forEach { zone ->
            jsonArray.put(zone.toJsonString())
        }
        prefs.edit().putString(KEY_ZONES, jsonArray.toString()).apply()
    }
    
    /**
     * No longer initializing demo zones to prevent unwanted alerts.
     */
    fun initializeDefaultZones() {
        // Demo zones removed to prevent "Majestic Junction" from appearing everywhere.
    }
}
