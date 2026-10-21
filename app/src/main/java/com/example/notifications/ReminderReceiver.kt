package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.DoseBuddyRepository
import com.example.data.SettingsRepository
import com.example.data.model.DoseRecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmScheduler.ACTION_DOSE_REMINDER) {
            val medicineId = intent.getLongExtra(AlarmScheduler.EXTRA_MEDICINE_ID, -1L)
            val scheduledTime = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME) ?: "08:00 AM"
            val slot = intent.getStringExtra(AlarmScheduler.EXTRA_SLOT) ?: AlarmScheduler.parseSlotFromTime(scheduledTime)
            val extraMedName = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICINE_NAME) ?: ""
            val extraDosage = intent.getStringExtra(AlarmScheduler.EXTRA_DOSAGE) ?: ""
            val extraMeal = intent.getStringExtra(AlarmScheduler.EXTRA_MEAL) ?: ""
            val passedDoseId = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_ID, -1L)

            Log.d(TAG, "Dose reminder broadcast received for medicineId=$medicineId, time=$scheduledTime, passedDoseId=$passedDoseId")

            val settingsRepo = SettingsRepository(context)
            if (!settingsRepo.notificationsEnabled.value) {
                Log.d(TAG, "Notifications disabled in settings, skipping reminder")
                return
            }

            val soundEnabled = settingsRepo.soundEnabled.value
            val voiceEnabled = settingsRepo.voiceRemindersEnabled.value
            val langCode = settingsRepo.languageCode.value

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val todayStr = DoseBuddyRepository.getTodayDateString()

                    if (medicineId > 0 || passedDoseId > 0) {
                        // Per-medicine reminder
                        var dose = if (passedDoseId > 0) {
                            db.doseRecordDao().getDoseById(passedDoseId)
                        } else null

                        if (dose == null && medicineId > 0) {
                            dose = db.doseRecordDao().getDoseByMedicineDateAndTime(medicineId, todayStr, scheduledTime)
                        }

                        val resolvedMedId = if (medicineId > 0) medicineId else (dose?.medicineId ?: -1L)
                        val med = if (resolvedMedId > 0) db.medicineDao().getMedicineById(resolvedMedId) else null
                        val medName = med?.name ?: dose?.medicineName ?: extraMedName.ifBlank { "Prescribed Medication" }
                        val medStrength = med?.strength ?: dose?.medicineStrength ?: ""
                        val dosage = med?.instructions ?: dose?.notes ?: extraDosage
                        val mealInstruction = med?.mealInstruction ?: dose?.mealInstruction ?: extraMeal

                        // If user has already marked this dose Taken or Skipped, prevent duplicate reminder
                        if (dose != null && (dose.status.equals("TAKEN", ignoreCase = true) || dose.status.equals("SKIPPED", ignoreCase = true))) {
                            Log.d(TAG, "Dose for $medName at $scheduledTime already marked ${dose.status}. Suppressing duplicate notification.")
                        } else {
                            if (dose == null) {
                                val newDose = DoseRecordEntity(
                                    medicineId = resolvedMedId,
                                    medicineName = medName,
                                    medicineStrength = medStrength,
                                    slot = slot,
                                    scheduledTime = scheduledTime,
                                    scheduledDate = todayStr,
                                    status = "PENDING",
                                    mealInstruction = mealInstruction,
                                    notes = dosage
                                )
                                val newId = db.doseRecordDao().insertDose(newDose)
                                dose = newDose.copy(id = newId)
                            } else {
                                // Clear snooze since this reminder is firing now
                                db.doseRecordDao().clearDoseSnooze(dose.id)
                            }

                            val notificationId = AlarmScheduler.getReminderRequestCode(resolvedMedId, scheduledTime)

                            // Play distinctive repeating alert audio sound if sound is enabled
                            if (soundEnabled) {
                                AlertSoundManager.play(context)
                            }

                            NotificationHelper.showDoseReminder(
                                context = context,
                                medicineName = medName,
                                strength = medStrength,
                                dosageInstructions = dosage,
                                slot = slot,
                                scheduledTime = scheduledTime,
                                mealInstruction = mealInstruction,
                                doseId = dose?.id ?: -1L,
                                soundEnabled = soundEnabled,
                                notificationId = notificationId
                            )
                        }

                        // Reschedule this medicine's reminder for the next day
                        AlarmScheduler.scheduleMedicineReminder(
                            context = context,
                            medicineId = medicineId,
                            medicineName = medName,
                            dosage = dosage,
                            mealInstruction = mealInstruction,
                            timeString = scheduledTime
                        )
                    } else {
                        // Legacy slot fallback
                        val todayDoses = db.doseRecordDao().getDosesForDate(todayStr).first()
                        val pendingSlotDoses = todayDoses.filter {
                            it.slot.equals(slot, ignoreCase = true) &&
                                    it.status.equals("PENDING", ignoreCase = true)
                        }

                        if (pendingSlotDoses.isNotEmpty()) {
                            val summaryMedicineName = if (pendingSlotDoses.size == 1) {
                                pendingSlotDoses[0].medicineName
                            } else {
                                "${pendingSlotDoses[0].medicineName} + ${pendingSlotDoses.size - 1} more"
                            }
                            val strength = if (pendingSlotDoses.size == 1) pendingSlotDoses[0].medicineStrength else ""
                            val firstDose = pendingSlotDoses[0]
                            val mealInstruction = if (pendingSlotDoses.size == 1) firstDose.mealInstruction else ""
                            val primaryDoseId = if (pendingSlotDoses.size == 1) firstDose.id else -1L

                            NotificationHelper.showDoseReminder(
                                context = context,
                                medicineName = summaryMedicineName,
                                strength = strength,
                                dosageInstructions = firstDose.notes,
                                slot = slot,
                                scheduledTime = scheduledTime,
                                mealInstruction = mealInstruction,
                                doseId = primaryDoseId,
                                soundEnabled = soundEnabled,
                                notificationId = slot.hashCode()
                            )
                        }

                        val scheduleTimes = settingsRepo.scheduleTimes.value
                        AlarmScheduler.scheduleDailyReminders(context, scheduleTimes)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling dose reminder broadcast: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
