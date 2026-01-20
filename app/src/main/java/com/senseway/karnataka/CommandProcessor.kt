package com.senseway.karnataka

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * CommandProcessor routes voice commands to appropriate actions
 */
class CommandProcessor(private val context: Context) {
    
    private val voiceAssistant = VoiceAssistant.getInstance(context)
    private val transportHelper = TransportHelper(context)
    private val emergencyManager = EmergencyManager.getInstance(context)
    
    fun processCommand(command: String, detectedLanguage: String = "en") {
        val normalizedCommand = command.lowercase().trim()
        
        if (normalizedCommand.length < 3) return

        Log.d("CommandProcessor", "Processing: $normalizedCommand")
        
        when {
            // Flexible navigation matching
            normalizedCommand.contains("navigate") || 
            normalizedCommand.contains("direction") || 
            normalizedCommand.contains("go to") ||
            normalizedCommand.contains("ದಾರಿ") -> {
                handleNavigationCommand(normalizedCommand, detectedLanguage)
            }

            normalizedCommand.contains("transport") || 
            normalizedCommand.contains("ಸಾರಿಗೆ") ||
            normalizedCommand.contains("bus") || 
            normalizedCommand.contains("metro") -> {
                handleTransportCommand(normalizedCommand, detectedLanguage)
            }
            
            normalizedCommand.contains("emergency") || 
            normalizedCommand.contains("help") ||
            normalizedCommand.contains("ಅನಾಹುತ") -> {
                handleEmergency(detectedLanguage)
            }
            
            normalizedCommand.contains("stop alarm") || 
            normalizedCommand.contains("i am okay") ||
            normalizedCommand.contains("ನಿಲ್ಲಿಸು") -> {
                handleStopAlarm(detectedLanguage)
            }
            
            normalizedCommand.contains("describe") || normalizedCommand.contains("scene") -> {
                handleSceneDescription(detectedLanguage)
            }
            
            normalizedCommand.contains("money") || normalizedCommand.contains("identify") -> {
                handleMoneyIdentification(detectedLanguage)
            }
            
            else -> {
                if (normalizedCommand.split(" ").size >= 2) {
                    val response = if (detectedLanguage == "kn") "ಕ್ಷಮಿಸಿ, ಅರ್ಥವಾಗಲಿಲ್ಲ." else "Command not understood."
                    voiceAssistant.speak(response, detectedLanguage)
                }
            }
        }
    }
    
    private fun handleNavigationCommand(command: String, language: String) {
        // Extract destination by removing keywords
        var destination = command
            .replace("navigate to", "")
            .replace("navigate", "")
            .replace("direction to", "")
            .replace("direction", "")
            .replace("go to", "")
            .replace("ದಾರಿ", "")
            .trim()

        if (destination.isNotEmpty()) {
            val response = if (language == "kn") "$destination ಗೆ ದಾರಿ" else "Navigating to $destination"
            voiceAssistant.speak(response, language)
            transportHelper.openRouteToDestination(destination)
        } else {
            voiceAssistant.speak(if (language == "kn") "ಎಲ್ಲಿಗೆ ಹೋಗಬೇಕು?" else "Where would you like to go?", language)
        }
    }

    private fun handleTransportCommand(command: String, language: String) {
        voiceAssistant.speak(if (language == "kn") "ಸಾರಿಗೆ ಮೋಡ್" else "Opening transport mode", language)
        transportHelper.openTransportMode()
    }
    
    private fun handleEmergency(language: String) {
        emergencyManager.triggerEmergency()
    }
    
    private fun handleSceneDescription(language: String) {
        val intent = Intent(context, SceneDescriptionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    private fun handleMoneyIdentification(language: String) {
        val intent = Intent(context, MoneyIdentifierActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    private fun handleStopAlarm(language: String) {
        emergencyManager.cancelAlarm()
    }

    private fun handleEmergencyContact(language: String) {
        val intent = Intent(context, EmergencyContactActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
