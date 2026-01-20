package com.senseway.karnataka

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/**
 * EmergencyContactActivity allows users to set emergency contact
 */
class EmergencyContactActivity : AppCompatActivity() {
    
    private lateinit var etContactName: EditText
    private lateinit var etContactPhone: EditText
    private lateinit var btnSaveContact: Button
    private lateinit var btnVoiceAdd: ImageButton
    private lateinit var rvContacts: RecyclerView
    
    private val contactStorage by lazy { EmergencyContactStorage(this) }
    private val contactsAdapter = ContactsAdapter()
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val voiceAssistant by lazy { VoiceAssistant.getInstance(this) }
    
    // Voice Input State Machine
    private enum class VoiceState { IDLE, LISTENING_NAME, LISTENING_PHONE }
    private var currentVoiceState = VoiceState.IDLE
    private var tempName = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_contact)
        
        etContactName = findViewById(R.id.etContactName)
        etContactPhone = findViewById(R.id.etContactPhone)
        btnSaveContact = findViewById(R.id.btnSaveContact)
        btnVoiceAdd = findViewById(R.id.btnVoiceAdd)
        rvContacts = findViewById(R.id.rvContacts)
        
        rvContacts.adapter = contactsAdapter
        
        loadContacts()
        
        btnSaveContact.setOnClickListener {
            saveContact()
        }
        
        btnVoiceAdd.setOnClickListener {
            startVoiceAddFlow()
        }
        
        initializeSpeechRecognizer()
    }
    
    private fun loadContacts() {
        val contacts = contactStorage.getEmergencyContacts()
        contactsAdapter.submitList(contacts)
    }
    
    private fun saveContact() {
        val name = etContactName.text.toString().trim()
        val phone = etContactPhone.text.toString().trim()
        
        if (name.isEmpty() || phone.isEmpty()) {
            voiceAssistant.speak("Please enter both name and phone number")
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        
        val contact = EmergencyContact(name, phone)
        contactStorage.saveEmergencyContact(contact)
        
        etContactName.text.clear()
        etContactPhone.text.clear()
        
        loadContacts()
        voiceAssistant.speak("Contact $name added successfully", flush = true)
        Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show()
    }
    
    // --- Voice Input Logic ---
    
    private fun startVoiceAddFlow() {
        if (!checkPermission()) {
            requestPermission()
            return
        }
        
        currentVoiceState = VoiceState.LISTENING_NAME
        voiceAssistant.speak("Please say the contact name")
        
        waitForTtsAndListen()
    }
    
    private fun waitForTtsAndListen() {
        // Recursively check if TTS is still speaking
        val checkInterval = 500L
        val handler = Handler(Looper.getMainLooper())
        
        val checkRunnable = object : Runnable {
            override fun run() {
                if (voiceAssistant.isSpeaking()) {
                    // Still speaking, check again later
                    handler.postDelayed(this, checkInterval)
                } else {
                    // Finished speaking, start listening (with small buffer)
                     handler.postDelayed({
                        startListening()
                    }, 300)
                }
            }
        }
        handler.post(checkRunnable)
    }
    
    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }
                
                override fun onError(error: Int) {
                    isListening = false
                    if (currentVoiceState != VoiceState.IDLE) {
                        voiceAssistant.speak("I didn't catch that. Please try again.")
                        currentVoiceState = VoiceState.IDLE
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        handleVoiceInput(text)
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }
    
    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        try {
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun handleVoiceInput(text: String) {
        when (currentVoiceState) {
            VoiceState.LISTENING_NAME -> {
                tempName = text
                etContactName.setText(tempName)
                voiceAssistant.speak("Got it. Now say the phone number.")
                currentVoiceState = VoiceState.LISTENING_PHONE
                
                waitForTtsAndListen()
            }
            VoiceState.LISTENING_PHONE -> {
                // Strip non-numeric for phone, keep + if exists
                val phone = text.replace("[^0-9+]".toRegex(), "")
                etContactPhone.setText(phone)
                
                voiceAssistant.speak("Saving contact $tempName.")
                currentVoiceState = VoiceState.IDLE
                saveContact()
            }
            else -> {}
        }
    }
    
    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceAddFlow()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
    
    // --- End Voice Input Logic ---
    
    private fun deleteContact(contact: EmergencyContact) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Remove ${contact.name}?")
            .setPositiveButton("Delete") { _, _ ->
                contactStorage.deleteEmergencyContact(contact)
                loadContacts()
                voiceAssistant.speak("Contact deleted")
                Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class ContactsAdapter : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {
        private var list: List<EmergencyContact> = emptyList()

        fun submitList(newList: List<EmergencyContact>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            return ContactViewHolder(view)
        }

        override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
            val contact = list[position]
            holder.bind(contact)
        }

        override fun getItemCount() = list.size

        inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val text1: TextView = itemView.findViewById(android.R.id.text1)
            private val text2: TextView = itemView.findViewById(android.R.id.text2)

            fun bind(contact: EmergencyContact) {
                text1.text = contact.name
                text2.text = contact.phoneNumber
                
                itemView.setOnLongClickListener {
                    deleteContact(contact)
                    true
                }
            }
        }
    }
}
