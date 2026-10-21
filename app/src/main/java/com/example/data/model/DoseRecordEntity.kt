package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dose_records")
data class DoseRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicineId: Long,
    val medicineName: String,
    val medicineStrength: String,
    val slot: String, // "MORNING", "AFTERNOON", "NIGHT"
    val scheduledTime: String, // e.g. "08:00 AM"
    val scheduledDate: String, // "YYYY-MM-DD"
    val status: String = "PENDING", // "PENDING", "TAKEN", "SKIPPED"
    val actionTimestamp: Long? = null,
    val notes: String = "",
    // Feature 2: Meal instruction preserved with reminder
    val mealInstruction: String = "No meal instruction specified",
    val mealInstructionRaw: String = "",
    // Feature 3: Track units deducted to prevent duplicates and enable exact undo
    val doseDeducted: Int = 0,
    // Feature: Snooze state tracking
    val snoozedUntil: Long = 0L
)

