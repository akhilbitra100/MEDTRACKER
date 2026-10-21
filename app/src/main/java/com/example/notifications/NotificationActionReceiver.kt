package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.data.AppDatabase
import com.example.data.DoseBuddyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionRx"
        const val ACTION_MARK_TAKEN = "com.example.medtracker.ACTION_MARK_TAKEN"
        const val ACTION_MARK_SKIPPED = "com.example.medtracker.ACTION_MARK_SKIPPED"
        const val EXTRA_DOSE_ID = "extra_dose_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val doseId = intent.getLongExtra(EXTRA_DOSE_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        Log.d(TAG, "Notification action received: $action for doseId=$doseId, notificationId=$notificationId")

        // Stop alert sound and voice speech immediately when Taken or Skipped is tapped
        AlertSoundManager.stop()
        com.example.ui.screens.MedicineAlertActivity.stopCurrentSpeech()

        // Dismiss the notification if valid notificationId provided
        if (notificationId != -1) {
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)
            } catch (_: Exception) {}
        }

        if (doseId != -1L) {
            AlarmScheduler.cancelSnooze(context, doseId)
        }

        if (doseId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val repository = DoseBuddyRepository(db.medicineDao(), db.doseRecordDao())

                when (action) {
                    ACTION_MARK_TAKEN -> {
                        repository.markDoseTaken(doseId)
                        Log.d(TAG, "Marked dose $doseId as TAKEN from notification action")
                    }
                    ACTION_MARK_SKIPPED -> {
                        repository.markDoseSkipped(doseId)
                        Log.d(TAG, "Marked dose $doseId as SKIPPED from notification action")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing notification action: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
