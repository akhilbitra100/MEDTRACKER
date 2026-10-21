package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.ScheduleTimes
import com.example.data.model.MedicineEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val ACTION_DOSE_REMINDER = "com.example.medtracker.ACTION_DOSE_REMINDER"
    const val EXTRA_SLOT = "extra_slot"
    const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
    const val EXTRA_MEDICINE_ID = "extra_medicine_id"
    const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
    const val EXTRA_DOSAGE = "extra_dosage"
    const val EXTRA_MEAL = "extra_meal"
    const val EXTRA_DOSE_ID = "extra_dose_id"

    private const val REQUEST_CODE_MORNING = 1001
    private const val REQUEST_CODE_AFTERNOON = 1002
    private const val REQUEST_CODE_NIGHT = 1003

    fun getReminderRequestCode(medicineId: Long, timeString: String): Int {
        val normalized = timeString.trim().uppercase(Locale.US)
        val hash = (medicineId.hashCode() * 31) xor normalized.hashCode()
        return (hash and 0x7FFFFFFF)
    }

    fun parseSlotFromTime(timeString: String): String {
        val clean = timeString.trim().uppercase(Locale.US)
        val matchedSlot = com.example.data.model.TimeSlot.findSlotForTime(clean)
        if (matchedSlot != null) {
            return matchedSlot.id
        }
        return try {
            val sdf12 = SimpleDateFormat("hh:mm a", Locale.US)
            val date = sdf12.parse(clean)
            if (date != null) {
                val cal = Calendar.getInstance().apply { time = date }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                when (hour) {
                    in 5..11 -> "MORNING"
                    in 12..16 -> "AFTERNOON"
                    in 17..18 -> "EVENING"
                    in 19..23 -> "NIGHT"
                    else -> if (clean.contains("PM")) "NIGHT" else "MORNING"
                }
            } else {
                if (clean.contains("AM")) "MORNING" else "NIGHT"
            }
        } catch (_: Exception) {
            if (clean.contains("AM")) "MORNING" else "NIGHT"
        }
    }

    fun formatTimeToAmPm(hourOfDay: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
        }
        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
        return sdf.format(cal.time)
    }

    fun scheduleMedicineReminder(
        context: Context,
        medicineId: Long,
        medicineName: String,
        dosage: String,
        mealInstruction: String,
        timeString: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val calendar = parseTimeToCalendar(timeString) ?: return

        // If time has already passed today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val slot = parseSlotFromTime(timeString)
        val requestCode = getReminderRequestCode(medicineId, timeString)

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DOSE_REMINDER
            putExtra(EXTRA_MEDICINE_ID, medicineId)
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_MEAL, mealInstruction)
            putExtra(EXTRA_SLOT, slot)
            putExtra(EXTRA_SCHEDULED_TIME, timeString)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = calendar.timeInMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled reminder for $medicineName (id=$medicineId) at $timeString (trigger: $triggerAtMillis)")
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for $medicineName: ${e.message}")
        }
    }

    fun cancelMedicineReminder(context: Context, medicineId: Long, timeString: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCode = getReminderRequestCode(medicineId, timeString)

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DOSE_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            try {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Cancelled reminder alarm for medicineId=$medicineId at $timeString")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling reminder: ${e.message}")
            }
        }
    }

    fun cancelAllRemindersForMedicine(context: Context, medicineId: Long, times: List<String>) {
        times.forEach { time ->
            cancelMedicineReminder(context, medicineId, time)
        }
    }

    fun getSnoozeRequestCode(doseId: Long): Int {
        val hash = (doseId.hashCode() * 37) xor 0x5002E
        return (hash and 0x7FFFFFFF)
    }

    fun scheduleSnooze(
        context: Context,
        doseId: Long,
        medicineId: Long,
        medicineName: String,
        dosage: String,
        mealInstruction: String,
        slot: String,
        scheduledTime: String,
        snoozeMinutes: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAtMillis = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val requestCode = getSnoozeRequestCode(doseId)

        // Cancel any existing snooze alarm first to prevent duplicates
        cancelSnooze(context, doseId)

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DOSE_REMINDER
            putExtra(EXTRA_MEDICINE_ID, medicineId)
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_MEAL, mealInstruction)
            putExtra(EXTRA_SLOT, slot)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_DOSE_ID, doseId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled snooze reminder for doseId=$doseId in $snoozeMinutes minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule snooze alarm: ${e.message}")
        }
    }

    fun cancelSnooze(context: Context, doseId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCode = getSnoozeRequestCode(doseId)

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DOSE_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            try {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Cancelled snooze alarm for doseId=$doseId")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling snooze: ${e.message}")
            }
        }
    }

    fun scheduleAllMedicineReminders(context: Context, medicines: List<MedicineEntity>) {
        medicines.forEach { med ->
            val times = med.getEffectiveReminderTimes()
            times.forEach { time ->
                scheduleMedicineReminder(
                    context = context,
                    medicineId = med.id,
                    medicineName = med.name,
                    dosage = med.instructions,
                    mealInstruction = med.mealInstruction,
                    timeString = time
                )
            }
        }
    }

    fun scheduleDailyReminders(context: Context, times: ScheduleTimes) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        scheduleSlotAlarm(context, alarmManager, "MORNING", times.morningTime, REQUEST_CODE_MORNING)
        scheduleSlotAlarm(context, alarmManager, "AFTERNOON", times.afternoonTime, REQUEST_CODE_AFTERNOON)
        scheduleSlotAlarm(context, alarmManager, "NIGHT", times.nightTime, REQUEST_CODE_NIGHT)
    }

    private fun scheduleSlotAlarm(
        context: Context,
        alarmManager: AlarmManager,
        slot: String,
        timeString: String,
        requestCode: Int
    ) {
        val calendar = parseTimeToCalendar(timeString) ?: return

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DOSE_REMINDER
            putExtra(EXTRA_SLOT, slot)
            putExtra(EXTRA_SCHEDULED_TIME, timeString)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = calendar.timeInMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun parseTimeToCalendar(timeString: String): Calendar? {
        return try {
            val format12 = SimpleDateFormat("hh:mm a", Locale.US)
            val date = format12.parse(timeString.trim()) ?: return null

            val parsedCal = Calendar.getInstance().apply { time = date }
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (_: Exception) {
            try {
                val format24 = SimpleDateFormat("HH:mm", Locale.US)
                val date = format24.parse(timeString.trim()) ?: return null
                val parsedCal = Calendar.getInstance().apply { time = date }
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
