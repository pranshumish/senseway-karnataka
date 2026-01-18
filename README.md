# SenseWay Karnataka 🚶‍♂️👁️
**The Zero-Cost, Offline-First Blind Assistant App**

SenseWay is a 100% hands-free, voice-controlled Android assistant designed for visually impaired users in Karnataka. It runs entirely offline, leveraging on-device sensors and ML Kit to provide navigation, safety, and vision assistance without needing expensive APIs or constant internet connectivity.

## 🌟 Core Features

### 🗣️ Voice-First Interface
- **Always-On Listening**: Runs as a Foreground Service to listen for commands even when the screen is off.
- **Bilingual Support**: Designed to handle both **English (India)** and **Kannada** commands.
- **Feedback**: Uses Text-to-Speech (TTS) for all interactions.

### 📍 Safety & Navigation
- **Danger Zones**: Users can mark unsafe locations (potholes, open drains) by voice. The app warns them via vibration and audio when they approach these zones again.
- **Transport Mode**: Voice command triggers Google Maps Transit navigation for BMTC/Namma Metro.
- **Fall Detection**: Uses the accelerometer to detect sudden drops and automatically trigger the emergency protocol.

### 🆘 Emergency Protocol
- **SOS Command**: "Emergency" or "Sahaya" triggers an immediate SOS.
- **Drop Failsafe**: If a fall is detected and the user doesn't say "I am okay" within a timeout, the app sends an SMS with the current location to emergency contacts.

### 👁️ Computer Vision (Zero-Cost)
- **Scene Description**: Uses **Google ML Kit (On-Device)** to detect objects (Chairs, Tables, People) offline.
- **Money Identification**: Identifies currency notes (demo feature).

---

## 🏗️ Architecture

- **Language**: Kotlin
- **Pattern**: MVVM (Service-based)
- **Services**: `VoiceAssistantService` (Foreground), `SensorEventListener` (Accelerometer)
- **Data**: `SharedPreferences` (JSON) for storing Danger Zones.
- **APIs Used**:
    - `android.speech.SpeechRecognizer`: Offline Voice Reco.
    - `android.location.LocationManager`: GPS Tracking.
    - `com.google.mlkit:object-detection`: Offline Vision.

---

## 🛠️ Setup Instructions

1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/pranshumish/senseway-karnataka.git
    ```
2.  **Open in Android Studio**:
    - Select "Open an Existing Project" and point to the `SenseWay` folder.
    - Wait for Gradle Sync to complete.
3.  **Run on Device**:
    - Connect a physical Android device (Recommended over Emulator for Mic/Sensors).
    - Enable "USB Debugging".
    - Click **Run**.
4.  **Permissions**:
    - Grant all requested permissions (Microphone, Location, SMS, etc.) on the first launch.

---

## 🗣️ Voice Commands

| Command | Action |
| :--- | :--- |
| **"Describe Scene" / "Nodu"** | Opens camera & identifies objects. |
| **"Add Danger Zone"** | Marks current GPS location as unsafe. |
| **"Transport Mode"** | Opens Bus/Metro navigation. |
| **"Emergency" / "Sahaya"** | Sends SOS SMS & calls contact. |
| **"Time" / "Samaya"** | Tells current time and date. |
| **"Stop" / "Nillisu"** | Stops any alarm or speech. |

---

## 📄 License
Open Source. Built for the community.
