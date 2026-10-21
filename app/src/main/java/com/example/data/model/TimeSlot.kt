package com.example.data.model

import com.example.notifications.AlarmScheduler
import java.util.Calendar

/**
 * Validated reminder time slots with strict permitted hour ranges:
 * - Morning: 5:00 AM to 11:59 AM
 * - Afternoon: 12:00 PM to 4:59 PM
 * - Evening: 5:00 PM to 6:59 PM
 * - Night: 7:00 PM to 11:59 PM
 */
enum class TimeSlot(
    val id: String,
    val displayName: String,
    val permittedRangeText: String,
    val startHour24: Int,
    val startMinute: Int,
    val endHour24: Int,
    val endMinute: Int
) {
    MORNING(
        id = "MORNING",
        displayName = "Morning",
        permittedRangeText = "5:00 AM to 11:59 AM",
        startHour24 = 5,
        startMinute = 0,
        endHour24 = 11,
        endMinute = 59
    ),
    AFTERNOON(
        id = "AFTERNOON",
        displayName = "Afternoon",
        permittedRangeText = "12:00 PM to 4:59 PM",
        startHour24 = 12,
        startMinute = 0,
        endHour24 = 16,
        endMinute = 59
    ),
    EVENING(
        id = "EVENING",
        displayName = "Evening",
        permittedRangeText = "5:00 PM to 6:59 PM",
        startHour24 = 17,
        startMinute = 0,
        endHour24 = 18,
        endMinute = 59
    ),
    NIGHT(
        id = "NIGHT",
        displayName = "Night",
        permittedRangeText = "7:00 PM to 11:59 PM",
        startHour24 = 19,
        startMinute = 0,
        endHour24 = 23,
        endMinute = 59
    );

    /**
     * All permitted hours (24-hour clock) for this slot.
     */
    val validHours24: List<Int>
        get() = (startHour24..endHour24).toList()

    /**
     * Validates whether a specific hour and minute fall within this slot.
     */
    fun isValid(hour24: Int, minute: Int): Boolean {
        val totalMinutes = hour24 * 60 + minute
        val start = startHour24 * 60 + startMinute
        val end = endHour24 * 60 + endMinute
        return totalMinutes in start..end
    }

    /**
     * Validates a formatted time string (e.g. "08:00 AM", "07:30 PM").
     */
    fun isValidTime(timeString: String): Boolean {
        val cal = AlarmScheduler.parseTimeToCalendar(timeString) ?: return false
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        return isValid(hour, min)
    }

    companion object {
        /**
         * Finds which slot matches a given time string.
         * Returns null if outside all allowed slot ranges (e.g. 1:00 AM to 4:59 AM).
         */
        fun findSlotForTime(timeString: String): TimeSlot? {
            val cal = AlarmScheduler.parseTimeToCalendar(timeString) ?: return null
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min = cal.get(Calendar.MINUTE)
            val totalMinutes = hour * 60 + min

            return values().firstOrNull { slot ->
                val start = slot.startHour24 * 60 + slot.startMinute
                val end = slot.endHour24 * 60 + slot.endMinute
                totalMinutes in start..end
            }
        }

        /**
         * Validates that a time string belongs to at least one valid slot.
         */
        fun isTimeInAnyValidSlot(timeString: String): Boolean {
            return findSlotForTime(timeString) != null
        }

        fun getExplanationForSlot(slot: TimeSlot): String {
            return "Permitted time range for ${slot.displayName}: ${slot.permittedRangeText}"
        }
    }
}
