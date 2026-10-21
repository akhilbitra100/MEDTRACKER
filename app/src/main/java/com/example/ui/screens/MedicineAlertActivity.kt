package com.example.ui.screens

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.example.R
import com.example.data.AppDatabase
import com.example.data.DoseBuddyRepository
import com.example.data.model.DoseRecordEntity
import com.example.data.model.MedicineEntity
import com.example.notifications.AlarmScheduler
import com.example.notifications.AlertSoundManager
import com.example.ui.theme.HealthTeal
import com.example.ui.theme.MedTrackerTheme
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.ui.theme.MedicalBlue
import com.example.ui.theme.StatusSkipOrange
import com.example.ui.theme.StatusTakenGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Debouncer to prevent duplicate voice announcements when the same alert is opened twice.
 */
object AlertSpeechDebouncer {
    private var lastKey: String = ""
    private var lastSpokenTime: Long = 0L
    private const val DEBOUNCE_WINDOW_MS = 25_000L

    @Synchronized
    fun shouldSpeak(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (key == lastKey && (now - lastSpokenTime) < DEBOUNCE_WINDOW_MS) {
            return false
        }
        lastKey = key
        lastSpokenTime = now
        return true
    }

    @Synchronized
    fun resetForTest() {
        lastKey = ""
        lastSpokenTime = 0L
    }
}

class MedicineAlertActivity : ComponentActivity() {

    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var hasAnnounced = false

    companion object {
        private const val TAG = "MedicineAlertActivity"
        private var activeAlertActivity: MedicineAlertActivity? = null

        fun stopCurrentSpeech() {
            activeAlertActivity?.stopSpeech()
        }

        const val EXTRA_DOSE_ID = "extra_dose_id"
        const val EXTRA_MEDICINE_ID = "extra_medicine_id"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_STRENGTH = "extra_strength"
        const val EXTRA_DOSAGE = "extra_dosage"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_MEAL = "extra_meal"
        const val EXTRA_SLOT = "extra_slot"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        fun createIntent(
            context: Context,
            doseId: Long,
            medicineId: Long,
            medicineName: String,
            strength: String,
            dosage: String,
            scheduledTime: String,
            mealInstruction: String,
            slot: String,
            notificationId: Int
        ): Intent {
            return Intent(context, MedicineAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_DOSE_ID, doseId)
                putExtra(EXTRA_MEDICINE_ID, medicineId)
                putExtra(EXTRA_MEDICINE_NAME, medicineName)
                putExtra(EXTRA_STRENGTH, strength)
                putExtra(EXTRA_DOSAGE, dosage)
                putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
                putExtra(EXTRA_MEAL, mealInstruction)
                putExtra(EXTRA_SLOT, slot)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
        }

        fun buildAlertSpeechText(
            medicineName: String,
            scheduledTime: String,
            dosage: String,
            mealInstruction: String,
            languageCode: String = "en"
        ): String {
            val cleanTime = scheduledTime.trim()
            val lang = languageCode.lowercase()

            val mealClean = mealInstruction.trim()
            val hasMeal = mealClean.isNotBlank() &&
                    !mealClean.contains("No meal", ignoreCase = true) &&
                    !mealClean.contains("No meal instruction specified", ignoreCase = true)

            val dosageClean = dosage.trim()
            val hasDosage = dosageClean.isNotBlank() &&
                    !dosageClean.equals("Take as directed", ignoreCase = true) &&
                    !dosageClean.equals("Take as prescribed", ignoreCase = true)

            return when (lang) {
                "hi" -> {
                    val base = "$medicineName लेने का समय हो गया है, जो $cleanTime के लिए निर्धारित है।"
                    val dosagePart = if (hasDosage) " खुराक निर्देश: $dosageClean।" else ""
                    val mealPart = if (hasMeal) " $mealClean।" else ""
                    "$base$dosagePart$mealPart"
                }
                "te" -> {
                    val base = "$cleanTime కి షెడ్యూల్ చేయబడిన $medicineName తీసుకునే సమయం ఆసన్నమైంది."
                    val dosagePart = if (hasDosage) " మోతాదు: $dosageClean." else ""
                    val mealPart = if (hasMeal) " $mealClean." else ""
                    "$base$dosagePart$mealPart"
                }
                else -> {
                    val base = "It's time to take $medicineName, scheduled for $cleanTime."
                    val dosagePart = if (hasDosage) " Dosage: $dosageClean." else ""
                    val mealPart = if (hasMeal) " Instructions: $mealClean." else ""
                    "$base$dosagePart$mealPart"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeAlertActivity = this
        enableEdgeToEdge()
        configureLockScreenPresentation()

        val doseId = intent.getLongExtra(EXTRA_DOSE_ID, -1L)
        val medicineId = intent.getLongExtra(EXTRA_MEDICINE_ID, -1L)
        val medicineName = intent.getStringExtra(EXTRA_MEDICINE_NAME) ?: "Prescribed Medication"
        val strength = intent.getStringExtra(EXTRA_STRENGTH) ?: ""
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        val scheduledTime = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: "Scheduled Time"
        val mealInstruction = intent.getStringExtra(EXTRA_MEAL) ?: ""
        val slot = intent.getStringExtra(EXTRA_SLOT) ?: "Scheduled Dose"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        // Start repeating alert sound if not already playing
        if (!AlertSoundManager.isAlertActive()) {
            AlertSoundManager.play(this)
        }

        // Initialize TextToSpeech voice announcement
        initTextToSpeech(
            medicineName = medicineName,
            scheduledTime = scheduledTime,
            dosage = dosage,
            mealInstruction = mealInstruction,
            doseId = doseId
        )

        setContent {
            MedTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MedicineAlertScreen(
                        doseId = doseId,
                        medicineId = medicineId,
                        initialMedicineName = medicineName,
                        initialStrength = strength,
                        initialDosage = dosage,
                        initialScheduledTime = scheduledTime,
                        initialMeal = mealInstruction,
                        initialSlot = slot,
                        notificationId = notificationId,
                        onStopSpeech = {
                            stopSpeech()
                        },
                        onDismissAlert = {
                            stopAlertSoundAndFinish()
                        }
                    )
                }
            }
        }
    }

    private fun initTextToSpeech(
        medicineName: String,
        scheduledTime: String,
        dosage: String,
        mealInstruction: String,
        doseId: Long
    ) {
        val alertKey = "${medicineName}_${scheduledTime}_$doseId"
        if (!AlertSpeechDebouncer.shouldSpeak(alertKey)) {
            Log.d(TAG, "Skipping duplicate voice announcement for $alertKey within debounce window")
            return
        }

        try {
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    if (!isFinishing && !isDestroyed && !hasAnnounced) {
                        hasAnnounced = true
                        speakAlertAnnouncement(medicineName, scheduledTime, dosage, mealInstruction)
                    }
                } else {
                    isTtsInitialized = false
                    Log.w(TAG, "TextToSpeech initialization failed with status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech: ${e.message}")
        }
    }

    private fun speakAlertAnnouncement(
        medicineName: String,
        scheduledTime: String,
        dosage: String,
        mealInstruction: String
    ) {
        val tts = textToSpeech ?: return
        if (!isTtsInitialized) return

        // Respect Android audio settings: if device is in silent mode, do not speak
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val ringerMode = audioManager?.ringerMode ?: android.media.AudioManager.RINGER_MODE_NORMAL
        if (ringerMode == android.media.AudioManager.RINGER_MODE_SILENT) {
            Log.d(TAG, "Device is in silent mode, skipping voice announcement.")
            return
        }

        val settingsRepo = com.example.data.SettingsRepository(this)
        if (!settingsRepo.voiceRemindersEnabled.value) {
            Log.d(TAG, "Voice reminders disabled in user settings, skipping voice announcement.")
            return
        }

        val languageCode = settingsRepo.languageCode.value

        val targetLocale = when (languageCode.lowercase()) {
            "hi" -> Locale("hi", "IN")
            "te" -> Locale("te", "IN")
            "en" -> Locale.US
            else -> Locale.getDefault()
        }

        val availability = try {
            tts.isLanguageAvailable(targetLocale)
        } catch (_: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }

        val effectiveLocale = if (availability >= TextToSpeech.LANG_AVAILABLE) {
            targetLocale
        } else {
            Locale.getDefault()
        }

        try {
            tts.language = effectiveLocale
        } catch (e: Exception) {
            Log.e(TAG, "Error setting language: ${e.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }

        val spokenText = buildAlertSpeechText(
            medicineName = medicineName,
            scheduledTime = scheduledTime,
            dosage = dosage,
            mealInstruction = mealInstruction,
            languageCode = effectiveLocale.language
        )

        // Stop any previous speech and speak announcement once (no loop)
        tts.stop()
        tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "alert_speech_${System.currentTimeMillis()}")
    }

    fun stopSpeech() {
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {}
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        stopSpeech()
        AlertSoundManager.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeAlertActivity == this) {
            activeAlertActivity = null
        }
        stopSpeech()
        try {
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {}
        AlertSoundManager.stop()
    }

    private fun configureLockScreenPresentation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun stopAlertSoundAndFinish() {
        stopSpeech()
        AlertSoundManager.stop()
        finish()
    }
}

@Composable
fun MedicineAlertScreen(
    doseId: Long,
    medicineId: Long,
    initialMedicineName: String,
    initialStrength: String,
    initialDosage: String,
    initialScheduledTime: String,
    initialMeal: String,
    initialSlot: String,
    notificationId: Int,
    onStopSpeech: () -> Unit = {},
    onDismissAlert: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isOperating by remember { mutableStateOf(false) }

    // Live details from database if available
    var medicineName by remember { mutableStateOf(initialMedicineName) }
    var strength by remember { mutableStateOf(initialStrength) }
    var dosage by remember { mutableStateOf(initialDosage) }
    var mealInstruction by remember { mutableStateOf(initialMeal) }
    var scheduledTime by remember { mutableStateOf(initialScheduledTime) }
    var resolvedDoseId by remember { mutableStateOf(doseId) }

    // Snooze slider state: min 5 mins, max 30 mins, default 10 mins
    var snoozeMinutesSlider by remember { mutableFloatStateOf(10f) }
    val snoozeMinutes = snoozeMinutesSlider.roundToInt().coerceIn(5, 30)

    val context = androidx.compose.ui.platform.LocalContext.current

    // Fetch fresh database record to ensure live accuracy
    LaunchedEffect(medicineId, resolvedDoseId) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                if (medicineId > 0) {
                    val med = db.medicineDao().getMedicineById(medicineId)
                    if (med != null) {
                        medicineName = med.name
                        strength = med.strength
                        if (med.instructions.isNotBlank()) dosage = med.instructions
                        if (med.mealInstruction.isNotBlank()) mealInstruction = med.mealInstruction
                    }
                }
                if (resolvedDoseId <= 0 && medicineId > 0) {
                    val todayStr = DoseBuddyRepository.getTodayDateString()
                    val dose = db.doseRecordDao().getDoseByMedicineDateAndTime(medicineId, todayStr, scheduledTime)
                    if (dose != null) {
                        resolvedDoseId = dose.id
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .testTag("medicine_alert_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Prominent Dose Due Now Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dose_due_badge"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "MEDICINE DOSE DUE NOW",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            // 2. Main Medicine Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("medicine_alert_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Medicine Icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MedicalBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = null,
                            tint = MedicalBlue,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Medicine Name
                    Text(
                        text = medicineName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.testTag("alert_medicine_name")
                    )

                    // Strength
                    if (strength.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strength,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MedicalBlue,
                                fontSize = 18.sp
                            ),
                            modifier = Modifier.testTag("alert_medicine_strength")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scheduled Time Chip
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scheduled: $scheduledTime",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("alert_scheduled_time")
                        )
                    }

                    // Confirmed Dosage Instructions
                    if (dosage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Dosage Instructions",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dosage,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.testTag("alert_dosage_instructions")
                                )
                            }
                        }
                    }

                    // Meal Instructions (when applicable)
                    if (mealInstruction.isNotBlank() && !mealInstruction.contains("No meal", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(HealthTeal.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restaurant,
                                contentDescription = null,
                                tint = HealthTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Meal Instruction: $mealInstruction",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.testTag("alert_meal_instruction")
                            )
                        }
                    }
                }
            }

            // 3. FEATURE 3: Snooze Slider Control Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alert_snooze_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = MedicalBlue,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Snooze Reminder",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        // Duration display next to slider
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$snoozeMinutes min",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("snooze_duration_label")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Slider: 5 to 30 minutes with step of 5 minutes (5 steps between 5 and 30)
                    Slider(
                        value = snoozeMinutesSlider,
                        onValueChange = { snoozeMinutesSlider = it },
                        valueRange = 5f..30f,
                        steps = 4, // 5, 10, 15, 20, 25, 30
                        colors = SliderDefaults.colors(
                            thumbColor = MedicalBlue,
                            activeTrackColor = MedicalBlue,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("snooze_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "5 min",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Text(
                            text = "30 min",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            if (isOperating) return@OutlinedButton
                            onStopSpeech()
                            isOperating = true
                            coroutineScope.launch {
                                try {
                                    val db = AppDatabase.getDatabase(context)
                                    val repository = DoseBuddyRepository(db.medicineDao(), db.doseRecordDao())
                                    val snoozedUntilMillis = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

                                    if (resolvedDoseId > 0) {
                                        repository.updateDoseSnooze(resolvedDoseId, snoozedUntilMillis)
                                    }

                                    // Schedule a new reminder using selected snooze duration
                                    AlarmScheduler.scheduleSnooze(
                                        context = context,
                                        doseId = resolvedDoseId,
                                        medicineId = medicineId,
                                        medicineName = medicineName,
                                        dosage = dosage,
                                        mealInstruction = mealInstruction,
                                        slot = initialSlot,
                                        scheduledTime = scheduledTime,
                                        snoozeMinutes = snoozeMinutes
                                    )

                                    // Dismiss active notification
                                    if (notificationId != -1) {
                                        NotificationManagerCompat.from(context).cancel(notificationId)
                                    }

                                    onDismissAlert()
                                } catch (_: Exception) {
                                    onDismissAlert()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_confirm_snooze"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Snooze for $snoozeMinutes Minutes",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // 4. FEATURE 4: Primary Action Buttons (Taken and Skip)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // TAKEN BUTTON (Large, prominent primary action)
                Button(
                    onClick = {
                        if (isOperating) return@Button
                        onStopSpeech()
                        isOperating = true
                        coroutineScope.launch {
                            try {
                                val db = AppDatabase.getDatabase(context)
                                val repository = DoseBuddyRepository(db.medicineDao(), db.doseRecordDao())

                                if (resolvedDoseId > 0) {
                                    repository.markDoseTaken(resolvedDoseId)
                                } else if (medicineId > 0) {
                                    val todayStr = DoseBuddyRepository.getTodayDateString()
                                    val dose = db.doseRecordDao().getDoseByMedicineDateAndTime(medicineId, todayStr, scheduledTime)
                                    if (dose != null) {
                                        repository.markDoseTaken(dose.id)
                                    } else {
                                        // Insert as TAKEN
                                        val newDose = DoseRecordEntity(
                                            medicineId = medicineId,
                                            medicineName = medicineName,
                                            medicineStrength = strength,
                                            slot = initialSlot,
                                            scheduledTime = scheduledTime,
                                            scheduledDate = todayStr,
                                            status = "TAKEN",
                                            actionTimestamp = System.currentTimeMillis(),
                                            mealInstruction = mealInstruction,
                                            notes = dosage
                                        )
                                        db.doseRecordDao().insertDose(newDose)
                                    }
                                }

                                // Cancel snooze / pending alarms for this dose
                                AlarmScheduler.cancelSnooze(context, resolvedDoseId)
                                if (notificationId != -1) {
                                    NotificationManagerCompat.from(context).cancel(notificationId)
                                }

                                onDismissAlert()
                            } catch (_: Exception) {
                                onDismissAlert()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("alert_btn_taken"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusTakenGreen)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "I Have Taken This Dose",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                    )
                }

                // SKIP BUTTON
                OutlinedButton(
                    onClick = {
                        if (isOperating) return@OutlinedButton
                        onStopSpeech()
                        isOperating = true
                        coroutineScope.launch {
                            try {
                                val db = AppDatabase.getDatabase(context)
                                val repository = DoseBuddyRepository(db.medicineDao(), db.doseRecordDao())

                                if (resolvedDoseId > 0) {
                                    repository.markDoseSkipped(resolvedDoseId)
                                } else if (medicineId > 0) {
                                    val todayStr = DoseBuddyRepository.getTodayDateString()
                                    val dose = db.doseRecordDao().getDoseByMedicineDateAndTime(medicineId, todayStr, scheduledTime)
                                    if (dose != null) {
                                        repository.markDoseSkipped(dose.id)
                                    }
                                }

                                AlarmScheduler.cancelSnooze(context, resolvedDoseId)
                                if (notificationId != -1) {
                                    NotificationManagerCompat.from(context).cancel(notificationId)
                                }

                                onDismissAlert()
                            } catch (_: Exception) {
                                onDismissAlert()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("alert_btn_skip"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StatusSkipOrange
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, StatusSkipOrange)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Skip This Dose",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            // Informational Disclaimer
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Full-screen presentation depends on Android device notification permissions and lock-screen settings.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
