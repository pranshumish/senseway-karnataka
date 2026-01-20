package com.senseway.karnataka

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GeminiResponse(val text: String?, val error: String? = null)

class GeminiPro {
    suspend fun getResponse(bitmap: Bitmap, prompt: String): GeminiResponse {
        val apiKey = BuildConfig.apiKey
        if (apiKey.isEmpty() || apiKey.startsWith("YOUR_")) {
            return GeminiResponse(null, error = "API Key not configured")
        }

        return withContext(Dispatchers.IO) {
            val scaledBitmap = scaleBitmap(bitmap)
            // 'gemini-1.5-flash' is returning 404 Not Found. We use 'gemini-flash-latest' which is verified to work.
            val modelsToTry = listOf("gemini-flash-latest", "gemini-2.0-flash", "gemini-1.5-flash")
            var lastError = ""

            for (modelName in modelsToTry) {
                try {
                    Log.d("GeminiPro", "Requesting $modelName...")
                    val generativeModel = GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey
                    )

                    val inputContent = content {
                        image(scaledBitmap)
                        text(prompt)
                    }

                    val response = generativeModel.generateContent(inputContent)
                    if (response.text != null) {
                        return@withContext GeminiResponse(response.text)
                    }
                } catch (e: Throwable) {
                    // Catch Throwable to handle potential SDK serialization crashes on error responses
                    lastError = "${e::class.simpleName}: ${e.message}" 
                    if (e.cause != null) lastError += " | Cause: ${e.cause?.message}"
                    Log.e("GeminiPro", "$modelName failed: $lastError")
                }
            }

            GeminiResponse(null, error = "Failed with all models. Last: $lastError")
        }
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val maxSize = 1024
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) return bitmap
        
        val ratio = width.toFloat() / height.toFloat()
        var newWidth = maxSize
        var newHeight = maxSize
        
        if (width > height) {
            newHeight = (maxSize / ratio).toInt()
        } else {
            newWidth = (maxSize * ratio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
