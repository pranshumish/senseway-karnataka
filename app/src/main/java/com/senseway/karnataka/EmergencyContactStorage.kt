package com.senseway.karnataka

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * EmergencyContactStorage manages emergency contact using SharedPreferences (FREE, offline)
 */
class EmergencyContactStorage(private val context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("emergency_contacts_prefs", Context.MODE_PRIVATE)
    private val KEY_CONTACTS = "contacts_list"
    
    fun saveEmergencyContact(contact: EmergencyContact) {
        val currentList = getEmergencyContacts().toMutableList()
        currentList.add(contact)
        saveList(currentList)
    }
    
    fun deleteEmergencyContact(contact: EmergencyContact) {
        val currentList = getEmergencyContacts().toMutableList()
        currentList.removeAll { it.phoneNumber == contact.phoneNumber } // precise removal
        saveList(currentList)
    }
    
    private fun saveList(list: List<EmergencyContact>) {
        val jsonString = Json.encodeToString(list)
        prefs.edit().putString(KEY_CONTACTS, jsonString).apply()
    }
    
    fun getEmergencyContacts(): List<EmergencyContact> {
        val jsonString = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()
        return try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
