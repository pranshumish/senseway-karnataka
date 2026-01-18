package com.karnataka.senseway

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager

class EmergencyDispatcher(private val context: Context) {

    // Helper to get Google Maps link
    fun getMapsLink(lat: Double, lon: Double): String {
        return "http://maps.google.com/maps?q=$lat,$lon"
    }

    fun sendEmergencySMS(phoneNumber: String, lat: Double, lon: Double) {
        try {
            val message = "EMERGENCY! I need help. My location: ${getMapsLink(lat, lon)}"
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun makeEmergencyCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$phoneNumber")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
