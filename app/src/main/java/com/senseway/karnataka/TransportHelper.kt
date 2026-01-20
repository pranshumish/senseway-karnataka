package com.senseway.karnataka

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.util.Log

/**
 * TransportHelper provides FREE navigation using Google Maps Intents
 * NO API KEY REQUIRED - Uses deep links and intents (completely free)
 */
class TransportHelper(private val context: Context) {
    
    private val warningZoneManager = WarningZoneManager.getInstance(context)
    
    /**
     * Open Google Maps in transit mode for navigation
     */
    fun openTransportMode() {
        warningZoneManager.getCurrentLocation { location ->
            if (location != null) {
                val uri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}&mode=t")
                startMapsIntent(uri)
            } else {
                openGoogleMapsApp()
            }
        }
    }
    
    /**
     * Open Google Maps with destination (transit mode)
     */
    fun openRouteToDestination(destination: String) {
        val encodedDest = Uri.encode(destination)
        val uri = Uri.parse("google.navigation:q=$encodedDest&mode=t")
        Log.d("TransportHelper", "Launching navigation to: $destination")
        startMapsIntent(uri)
    }
    
    /**
     * Search for bus route number (e.g., "500D")
     */
    fun searchBusRoute(routeNumber: String) {
        val query = "BMTC $routeNumber Bangalore"
        searchInGoogleMaps(query)
    }
    
    private fun startMapsIntent(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("TransportHelper", "Error starting Maps intent with package: ${e.message}")
            // Fallback: Try without explicit package
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                // Last resort: Browser
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/${uri.getQueryParameter("q")}"))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)
            }
        }
    }
    
    private fun openGoogleMapsApp() {
        val uri = Uri.parse("geo:0,0?q=Bangalore")
        startMapsIntent(uri)
    }
    
    private fun searchInGoogleMaps(query: String) {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        startMapsIntent(uri)
    }
    
    fun getTransitDirections(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double) {
        val url = "https://www.google.com/maps/dir/$fromLat,$fromLng/$toLat,$toLng/data=!4m2!4m1!3e3"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }
}
