package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceReminderManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L

    companion object {
        private const val TAG = "VoiceReminderManager"
        private const val MIN_REPEAT_INTERVAL_MS = 15000L // Prevent repeating within 15 seconds
    }

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech engine: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            Log.d(TAG, "TextToSpeech engine initialized successfully")
        } else {
            isInitialized = false
            Log.w(TAG, "TextToSpeech engine initialization failed with status: $status")
        }
    }

    /**
     * Speaks a dose reminder in the requested application language (English, Hindi, or Telugu).
     * Gracefully falls back to default voice if specific language voice pack is unavailable.
     */
    fun speakDoseReminder(
        medicineName: String,
        strength: String,
        mealInstruction: String,
        slot: String,
        languageCode: String,
        isTest: Boolean = false,
        onResult: ((success: Boolean, message: String) -> Unit)? = null
    ) {
        val ttsInstance = tts
        if (ttsInstance == null || !isInitialized) {
            val msg = "Text-to-speech service is initializing or unavailable on this device."
            Log.w(TAG, msg)
            onResult?.invoke(false, msg)
            return
        }

        // Determine target locale based on language code
        val targetLocale = when (languageCode.lowercase()) {
            "hi" -> Locale("hi", "IN")
            "te" -> Locale("te", "IN")
            "en" -> Locale.US
            else -> Locale.getDefault()
        }

        // Check if language is available
        val availability = try {
            ttsInstance.isLanguageAvailable(targetLocale)
        } catch (_: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }

        val effectiveLocale = if (availability >= TextToSpeech.LANG_AVAILABLE) {
            targetLocale
        } else {
            Log.w(TAG, "Voice for language ${targetLocale.displayName} not installed. Falling back to default.")
            Locale.getDefault()
        }

        try {
            ttsInstance.language = effectiveLocale
        } catch (e: Exception) {
            Log.e(TAG, "Error setting language: ${e.message}")
        }

        // Format spoken text according to language and instructions
        val spokenText = formatSpokenReminder(
            locale = effectiveLocale,
            medicineName = medicineName,
            strength = strength,
            mealInstruction = mealInstruction,
            slot = slot,
            isTest = isTest
        )

        // Avoid repeated announcements
        val now = System.currentTimeMillis()
        if (!isTest && spokenText == lastSpokenText && (now - lastSpokenTime) < MIN_REPEAT_INTERVAL_MS) {
            Log.d(TAG, "Skipping duplicate voice announcement within debounce window")
            onResult?.invoke(true, "Reminder already announced.")
            return
        }

        lastSpokenText = spokenText
        lastSpokenTime = now

        // Stop any ongoing speech before starting a new announcement
        ttsInstance.stop()

        val speakResult = ttsInstance.speak(
            spokenText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "dose_reminder_${System.currentTimeMillis()}"
        )

        if (speakResult == TextToSpeech.SUCCESS) {
            val statusNote = if (availability >= TextToSpeech.LANG_AVAILABLE) {
                "Speaking in ${effectiveLocale.displayLanguage}"
            } else {
                "Voice for ${targetLocale.displayLanguage} unavailable; spoke in ${effectiveLocale.displayLanguage}"
            }
            onResult?.invoke(true, statusNote)
        } else {
            onResult?.invoke(false, "Could not start audio playback.")
        }
    }

    private fun formatSpokenReminder(
        locale: Locale,
        medicineName: String,
        strength: String,
        mealInstruction: String,
        slot: String,
        isTest: Boolean
    ): String {
        val lang = locale.language.lowercase()

        val mealPhrase = when (mealInstruction.trim()) {
            "Before food" -> when (lang) {
                "hi" -> "भोजन से पहले"
                "te" -> "భోజనానికి ముందు"
                else -> "before food"
            }
            "After food" -> when (lang) {
                "hi" -> "भोजन के बाद"
                "te" -> "భోజనం తర్వాత"
                else -> "after food"
            }
            "With food" -> when (lang) {
                "hi" -> "भोजन के साथ"
                "te" -> "భోజనంతో పాటు"
                else -> "with food"
            }
            else -> ""
        }

        val slotFormatted = when (slot.uppercase()) {
            "MORNING" -> when (lang) {
                "hi" -> "सुबह की खुराक"
                "te" -> "ఉదయం డోస్"
                else -> "morning dose"
            }
            "AFTERNOON" -> when (lang) {
                "hi" -> "दोपहर की खुराक"
                "te" -> "మధ్యాహ్నం డోస్"
                else -> "afternoon dose"
            }
            "NIGHT" -> when (lang) {
                "hi" -> "रात की खुराक"
                "te" -> "రాత్రి డోస్"
                else -> "night dose"
            }
            else -> when (lang) {
                "hi" -> "दवा की खुराक"
                "te" -> "మందు డోస్"
                else -> "scheduled dose"
            }
        }

        return when (lang) {
            "hi" -> {
                val prefix = if (isTest) "परीक्षण अनुस्मारक: " else "दवा का समय: "
                val mealPart = if (mealPhrase.isNotEmpty()) ", $mealPhrase" else ""
                "$prefix$slotFormatted के लिए $medicineName $strength लें$mealPart।"
            }
            "te" -> {
                val prefix = if (isTest) "పరీక్ష రిమైండర్: " else "మందుల సమయం: "
                val mealPart = if (mealPhrase.isNotEmpty()) ", $mealPhrase" else ""
                "$prefix$slotFormatted కోసం $medicineName $strength తీసుకోండి$mealPart।"
            }
            else -> {
                val prefix = if (isTest) "Test voice reminder: " else "MedTracker reminder: "
                val mealPart = if (mealPhrase.isNotEmpty()) ", $mealPhrase" else ""
                "${prefix}Time for your $slotFormatted of $medicineName $strength$mealPart."
            }
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (_: Exception) {}
    }
}
