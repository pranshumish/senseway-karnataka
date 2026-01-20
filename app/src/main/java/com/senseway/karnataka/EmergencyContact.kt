package com.senseway.karnataka

import kotlinx.serialization.Serializable

/**
 * Data class for emergency contact
 */
@Serializable
data class EmergencyContact(
    val name: String,
    val phoneNumber: String
)
