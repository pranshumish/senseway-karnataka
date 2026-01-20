package com.karnataka.senseway

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

class DangerZoneManager(private val context: Context, private val onWarningFn: (String) -> Unit) : LocationListener {

    private var locationManager: LocationManager? = null
    private val prefs: SharedPreferences = context.getSharedPreferences("senseway_zones", Context.MODE_PRIVATE)
    private val ZONES_KEY = "danger_zones"
    private val WARNING_RADIUS_METERS = 30.0f
    
    // Simple in-memory list for active checking
    private val zones = ArrayList<Zone>()

    data class Zone(val lat: Double, val lon: Double, val name: String)

    init {
        loadZones()
    }

    @SuppressLint("MissingPermission") // Checked in Activity
    fun startTracking() {
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            // Request updates every 5 seconds or 5 meters
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopTracking() {
        locationManager?.removeUpdates(this)
    }

    fun addCurrentZone(name: String = "Danger Zone") {
        try {
            @SuppressLint("MissingPermission")
            val loc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            loc?.let {
                val newZone = Zone(it.latitude, it.longitude, name)
                zones.add(newZone)
                saveZones()
                onWarningFn("Added new danger zone at current location")
            }
        } catch(e: Exception) {
            onWarningFn("Could not get location to save zone")
        }
    }

    private fun saveZones() {
        val jsonArray = JSONArray()
        zones.forEach { zone ->
            val obj = JSONObject()
            obj.put("lat", zone.lat)
            obj.put("lon", zone.lon)
            obj.put("name", zone.name)
            jsonArray.put(obj)
        }
        prefs.edit().putString(ZONES_KEY, jsonArray.toString()).apply()
    }

    private fun loadZones() {
        val jsonString = prefs.getString(ZONES_KEY, null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    zones.add(Zone(obj.getDouble("lat"), obj.getDouble("lon"), obj.getString("name")))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        checkZones(location)
    }

    private fun checkZones(current: Location) {
        val target = Location("target")
        for (zone in zones) {
            target.latitude = zone.lat
            target.longitude = zone.lon
            
            if (current.distanceTo(target) < WARNING_RADIUS_METERS) {
                onWarningFn("Warning! Approaching ${zone.name}")
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
