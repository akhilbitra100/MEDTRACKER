package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val strength: String,
    val form: String = "Tablet",
    val instructions: String = "Take as directed",
    val frequency: String = "Once daily",
    val originalFrequency: String = "",
    val timingInstructions: String = "",
    val additionalInstructions: String = "",
    val morningSlot: Boolean = true,
    val afternoonSlot: Boolean = false,
    val nightSlot: Boolean = false,
    val durationDays: Int = 14,
    val isUncertain: Boolean = false,
    val uncertaintyReason: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // Feature 2: Smart Meal-Time Scheduling
    val mealInstruction: String = "No meal instruction specified", // "Before food", "After food", "With food", "No meal instruction specified"
    val mealInstructionRaw: String = "",
    val mealInstructionFlagged: Boolean = false,
    // Feature 3: Medicine Refill Alerts
    val currentQuantity: Int = 30,
    val quantityUnit: String = "tablets",
    val doseQuantity: Int = 1,
    val lowStockThreshold: Int = 5,
    val refillAlertEnabled: Boolean = true,
    // Feature 4: Per-Medicine Reminder Times (comma-separated, e.g. "08:00 AM, 08:00 PM")
    val reminderTimes: String = ""
) {
    fun getReminderTimesList(): List<String> {
        if (reminderTimes.isBlank()) return emptyList()
        return reminderTimes.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun getEffectiveReminderTimes(morningTime: String = "08:00 AM", afternoonTime: String = "01:00 PM", nightTime: String = "09:00 PM"): List<String> {
        val parsed = getReminderTimesList()
        if (parsed.isNotEmpty()) return parsed

        // Backward compatibility fallback for legacy entries that only had slot flags
        val fallback = mutableListOf<String>()
        if (morningSlot) fallback.add(morningTime)
        if (afternoonSlot) fallback.add(afternoonTime)
        if (nightSlot) fallback.add(nightTime)
        return fallback
    }
}

