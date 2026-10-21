package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.screens.MedicineAlertActivity

object NotificationHelper {
    const val CHANNEL_ID_SOUND = "medtracker_reminders_sound_channel"
    const val CHANNEL_ID_SILENT = "medtracker_reminders_silent_channel"
    const val CHANNEL_ID_COMPLETION = "medtracker_completion_channel"

    private const val CHANNEL_NAME_SOUND = "MedTracker Dose Alerts (Sound & Vibration)"
    private const val CHANNEL_NAME_SILENT = "MedTracker Dose Alerts (Silent)"
    private const val CHANNEL_NAME_COMPLETION = "MedTracker Daily Completion"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val soundUri = Uri.parse(
                "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.medtracker_chime}"
            )
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            // 1. Channel with bundled chime sound
            val soundChannel = NotificationChannel(
                CHANNEL_ID_SOUND,
                CHANNEL_NAME_SOUND,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to take scheduled medicines with bundled Dose Chime"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setSound(soundUri, audioAttributes)
            }

            // 2. Silent / Gentle Channel
            val silentChannel = NotificationChannel(
                CHANNEL_ID_SILENT,
                CHANNEL_NAME_SILENT,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Quiet dose reminder notifications without audio chime"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 150)
                setSound(null, null)
            }

            // 3. Completion Channel
            val completionChannel = NotificationChannel(
                CHANNEL_ID_COMPLETION,
                CHANNEL_NAME_COMPLETION,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily congratulations when all scheduled doses for the day are taken"
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }

            notificationManager.createNotificationChannel(soundChannel)
            notificationManager.createNotificationChannel(silentChannel)
            notificationManager.createNotificationChannel(completionChannel)
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showDoseReminder(
        context: Context,
        medicineName: String,
        strength: String,
        dosageInstructions: String = "",
        slot: String,
        scheduledTime: String,
        mealInstruction: String = "",
        doseId: Long = -1L,
        soundEnabled: Boolean = true,
        notificationId: Int = (System.currentTimeMillis() % 10000).toInt()
    ) {
        if (!canPostNotifications(context)) return

        // Build intent to launch dedicated full-screen medicine alert
        val alertIntent = MedicineAlertActivity.createIntent(
            context = context,
            doseId = doseId,
            medicineId = if (doseId > 0) doseId else -1L,
            medicineName = medicineName,
            strength = strength,
            dosage = dosageInstructions,
            scheduledTime = scheduledTime,
            mealInstruction = mealInstruction,
            slot = slot,
            notificationId = notificationId
        )

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            alertIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val slotFormatted = when (slot.uppercase()) {
            "MORNING" -> "Morning Dose"
            "AFTERNOON" -> "Afternoon Dose"
            "NIGHT" -> "Night Dose"
            else -> "Scheduled Dose"
        }

        val mealText = if (mealInstruction.isNotBlank() && !mealInstruction.contains("No meal", ignoreCase = true)) {
            " • $mealInstruction"
        } else ""

        val instructionsText = if (dosageInstructions.isNotBlank()) "\nDosage: $dosageInstructions" else ""

        val channelId = if (soundEnabled) CHANNEL_ID_SOUND else CHANNEL_ID_SILENT

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💊 $medicineName ($scheduledTime)")
            .setContentText("Time to take $medicineName ${strength.ifBlank { "" }}$mealText.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Time to take your $slotFormatted of $medicineName $strength ($scheduledTime)$mealText.$instructionsText\n\n(Tap to view full-screen medicine alert, snooze slider, and confirm dose.)")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)

        // Add action buttons if doseId is known
        if (doseId > 0) {
            val takenIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_TAKEN
                putExtra(NotificationActionReceiver.EXTRA_DOSE_ID, doseId)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val takenPendingIntent = PendingIntent.getBroadcast(
                context,
                (notificationId * 10 + 1),
                takenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val skipIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_SKIPPED
                putExtra(NotificationActionReceiver.EXTRA_DOSE_ID, doseId)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val skipPendingIntent = PendingIntent.getBroadcast(
                context,
                (notificationId * 10 + 2),
                skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.checkbox_on_background, "Taken", takenPendingIntent)
            builder.addAction(android.R.drawable.ic_delete, "Skip", skipPendingIntent)
        }

        if (soundEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val soundUri = Uri.parse(
                "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.medtracker_chime}"
            )
            builder.setSound(soundUri)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showDailyCompletionNotification(
        context: Context,
        dosesCount: Int,
        soundEnabled: Boolean = true
    ) {
        if (!canPostNotifications(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            999,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_COMPLETION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎉 All Doses Completed for Today!")
            .setContentText("Great job! You have taken all $dosesCount scheduled medications today.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Outstanding adherence! Every scheduled dose ($dosesCount medicines) for today has been marked Taken. Keep up the healthy routine!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (soundEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val soundUri = Uri.parse(
                "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.medtracker_chime}"
            )
            builder.setSound(soundUri)
        }

        try {
            NotificationManagerCompat.from(context).notify(999, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showTestReminder(context: Context, soundEnabled: Boolean = true) {
        showDoseReminder(
            context = context,
            medicineName = "Amoxicillin",
            strength = "500 mg",
            dosageInstructions = "Take 1 capsule with food",
            mealInstruction = "With food",
            slot = "MORNING",
            scheduledTime = "08:00 AM",
            doseId = 1L,
            soundEnabled = soundEnabled,
            notificationId = 101
        )
    }

    // Backwards compatibility alias
    fun createNotificationChannel(context: Context) = createNotificationChannels(context)
}
