package com.senseway.karnataka

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * VoiceAssistant handles Text-to-Speech in Kannada and English
 * Singleton pattern with priority queuing support.
 */
class VoiceAssistant private constructor(private val context: Context) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    companion object {
        @Volatile
        private var INSTANCE: VoiceAssistant? = null

        fun getInstance(context: Context): VoiceAssistant {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceAssistant(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    init {
        tts = TextToSpeech(context, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setLanguage(Locale.ENGLISH)
        } else {
            Log.e("VoiceAssistant", "TTS initialization failed")
        }
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }
    
    /**
     * Speak text.
     * @param flush If true (default), interrupts current speech. Use false for background warnings.
     */
    fun speak(text: String, language: String = "en", flush: Boolean = true) {
        if (!isInitialized || tts == null) return
        
        try {
            val locale = when (language.lowercase()) {
                "kn", "kannada", "kn-in" -> Locale("kn", "IN")
                else -> Locale("en", "IN")
            }
            
            val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            
            tts?.setLanguage(locale)
            tts?.speak(text, queueMode, null, "utterance_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e("VoiceAssistant", "Error speaking: ${e.message}")
        }
    }
    
    fun stop() {
        tts?.stop()
    }
    
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        INSTANCE = null
    }
}
