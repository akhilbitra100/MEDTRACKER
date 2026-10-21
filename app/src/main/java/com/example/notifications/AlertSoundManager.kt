package com.example.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.R
import com.example.data.SettingsRepository

/**
 * Singleton manager to play and stop distinctive repeating reminder alert sounds.
 * Avoids multiple overlapping players, respects Android audio/vibrate settings,
 * and enforces a safety timeout to prevent infinite sound loops.
 */
object AlertSoundManager {
    private const val TAG = "AlertSoundManager"
    private const val MAX_PLAY_DURATION_MS = 60_000L // 60 seconds auto-timeout

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null

    private val stopRunnable = Runnable {
        Log.d(TAG, "Auto-stopping alert sound after timeout.")
        stop()
    }

    @Synchronized
    fun play(context: Context) {
        val appContext = context.applicationContext

        // Check user settings for reminder sound
        val settingsRepo = SettingsRepository(appContext)
        if (!settingsRepo.soundEnabled.value) {
            Log.d(TAG, "Sound is disabled in user settings, skipping audio alert.")
            return
        }

        // Check device audio mode
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

        // In silent mode without vibration, respect user silence
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            Log.d(TAG, "Device is in silent mode, skipping audio.")
            return
        }

        // Stop any currently playing audio before starting
        stop()

        // Handle vibration
        startVibration(appContext)

        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            Log.d(TAG, "Device is in vibrate-only mode, vibration active without audio.")
            isPlaying = true
            handler.postDelayed(stopRunnable, MAX_PLAY_DURATION_MS)
            return
        }

        try {
            val soundUri = Uri.parse("android.resource://${appContext.packageName}/${R.raw.medtracker_chime}")
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(appContext, soundUri)
                isLooping = true
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    stop()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            isPlaying = true

            // Safety timeout to avoid endless sound loop
            handler.removeCallbacks(stopRunnable)
            handler.postDelayed(stopRunnable, MAX_PLAY_DURATION_MS)
            Log.d(TAG, "Alert sound started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alert sound: ${e.message}", e)
            stop()
        }
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacks(stopRunnable)
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
            isPlaying = false
        }

        stopVibration()
        Log.d(TAG, "Alert sound stopped.")
    }

    fun isAlertActive(): Boolean = isPlaying

    private fun startVibration(context: Context) {
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator = vib

            val pattern = longArrayOf(0, 400, 300, 400, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start vibration: ${e.message}")
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling vibration: ${e.message}")
        } finally {
            vibrator = null
        }
    }
}
