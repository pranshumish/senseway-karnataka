package com.karnataka.senseway

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class SenseWayApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Assistant Service",
                NotificationManager.IMPORTANCE_HIGH // High importance for always-on visibility
            ).apply {
                description = "Channel for SenseWay Voice Assistant"
                setSound(null, null) // Silent notification to not disturb voice reco
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "senseway_voice_service_channel"
    }
}
