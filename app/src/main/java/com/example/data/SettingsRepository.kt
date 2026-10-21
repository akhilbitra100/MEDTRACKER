package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScheduleTimes(
    val morningTime: String = "08:00 AM",
    val afternoonTime: String = "01:00 PM",
    val nightTime: String = "09:00 PM"
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences? = try {
        context.getSharedPreferences("medtracker_settings", Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    private fun safeGetString(key: String, default: String): String {
        return try {
            prefs?.getString(key, default) ?: default
        } catch (_: Throwable) {
            default
        }
    }

    private fun safeGetBoolean(key: String, default: Boolean): Boolean {
        return try {
            prefs?.getBoolean(key, default) ?: default
        } catch (_: Throwable) {
            default
        }
    }

    private val _scheduleTimes = MutableStateFlow(
        ScheduleTimes(
            morningTime = safeGetString("morning_time", "08:00 AM"),
            afternoonTime = safeGetString("afternoon_time", "01:00 PM"),
            nightTime = safeGetString("night_time", "09:00 PM")
        )
    )
    val scheduleTimes: StateFlow<ScheduleTimes> = _scheduleTimes.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(
        safeGetBoolean("notifications_enabled", true)
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _soundEnabled = MutableStateFlow(
        safeGetBoolean("sound_enabled", true)
    )
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(
        "LIGHT" // Consistent light theme throughout application
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _voiceRemindersEnabled = MutableStateFlow(
        safeGetBoolean("voice_reminders_enabled", true)
    )
    val voiceRemindersEnabled: StateFlow<Boolean> = _voiceRemindersEnabled.asStateFlow()

    private val _languageCode = MutableStateFlow(
        safeGetString("language_code", "system") // "system", "en", "hi", "te"
    )
    val languageCode: StateFlow<String> = _languageCode.asStateFlow()


    fun updateTimes(morning: String, afternoon: String, night: String) {
        val safeMorning = morning.ifBlank { "08:00 AM" }
        val safeAfternoon = afternoon.ifBlank { "01:00 PM" }
        val safeNight = night.ifBlank { "09:00 PM" }
        try {
            prefs?.edit()
                ?.putString("morning_time", safeMorning)
                ?.putString("afternoon_time", safeAfternoon)
                ?.putString("night_time", safeNight)
                ?.apply()
        } catch (_: Throwable) {}
        _scheduleTimes.value = ScheduleTimes(safeMorning, safeAfternoon, safeNight)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        try {
            prefs?.edit()?.putBoolean("notifications_enabled", enabled)?.apply()
        } catch (_: Throwable) {}
        _notificationsEnabled.value = enabled
    }

    fun setSoundEnabled(enabled: Boolean) {
        try {
            prefs?.edit()?.putBoolean("sound_enabled", enabled)?.apply()
        } catch (_: Throwable) {}
        _soundEnabled.value = enabled
    }

    fun setThemeMode(mode: String) {
        val safeMode = mode.ifBlank { "LIGHT" }
        try {
            prefs?.edit()?.putString("theme_mode", safeMode)?.apply()
        } catch (_: Throwable) {}
        _themeMode.value = safeMode
    }

    fun setVoiceRemindersEnabled(enabled: Boolean) {
        try {
            prefs?.edit()?.putBoolean("voice_reminders_enabled", enabled)?.apply()
        } catch (_: Throwable) {}
        _voiceRemindersEnabled.value = enabled
    }


    fun setLanguageCode(code: String) {
        val safeCode = code.ifBlank { "system" }
        try {
            prefs?.edit()?.putString("language_code", safeCode)?.apply()
        } catch (_: Throwable) {}
        _languageCode.value = safeCode
    }

    fun getLastCompletionDateNotified(): String {
        return safeGetString("last_completion_notified_date", "")
    }

    fun setLastCompletionDateNotified(date: String) {
        try {
            prefs?.edit()?.putString("last_completion_notified_date", date)?.apply()
        } catch (_: Throwable) {}
    }

    fun hasSeededDemo(): Boolean {
        return safeGetBoolean("has_seeded_demo", false)
    }

    fun setSeededDemo(seeded: Boolean) {
        try {
            prefs?.edit()?.putBoolean("has_seeded_demo", seeded)?.apply()
        } catch (_: Throwable) {}
    }
}
