package com.karnataka.senseway

class CommandRouter {

    enum class Action {
        NONE,
        TRANSPORT_MODE,
        ADD_DANGER_ZONE,
        EMERGENCY_SOS,
        DESCRIBE_SCENE,
        IDENTIFY_MONEY,
        STOP_ALARM,
        I_AM_OKAY,
        GET_TIME
    }

    fun processCommand(text: String): Action {
        val lower = text.lowercase()
        
        // Emergency High Priority
        if (lower.contains("emergency") || lower.contains("help") || lower.contains("sahaya")) return Action.EMERGENCY_SOS
        if (lower.contains("stop") || lower.contains("nillisu")) return Action.STOP_ALARM
        if (lower.contains("okay") || lower.contains("sari")) return Action.I_AM_OKAY
        
        // Utilities
        if (lower.contains("transport") || lower.contains("bus") || lower.contains("metro")) return Action.TRANSPORT_MODE
        if (lower.contains("danger") || lower.contains("zone") || lower.contains("apaya")) return Action.ADD_DANGER_ZONE
        if (lower.contains("scene") || lower.contains("look") || lower.contains("node")) return Action.DESCRIBE_SCENE
        if (lower.contains("money") || lower.contains("cash") || lower.contains("duddu")) return Action.IDENTIFY_MONEY
        if (lower.contains("time") || lower.contains("samaya")) return Action.GET_TIME

        return Action.NONE
    }
}
