package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "android.intent.action.MY_PACKAGE_REPLACED"
        ) {
            Log.d(TAG, "Device rebooted or package replaced (${intent.action}). Restoring active medicine reminders.")
            val settingsRepo = SettingsRepository(context)
            if (!settingsRepo.notificationsEnabled.value) {
                Log.d(TAG, "Notifications disabled in settings, skipping reminder restoration on boot.")
                return
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val medicines = db.medicineDao().getAllMedicines().first()
                    AlarmScheduler.scheduleAllMedicineReminders(context, medicines)
                    Log.d(TAG, "Restored reminders for ${medicines.size} active medications on device reboot.")

                    // Also restore daily fallback schedule if needed
                    val times = settingsRepo.scheduleTimes.value
                    AlarmScheduler.scheduleDailyReminders(context, times)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore reminders on device boot: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
