package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ExtractedMedicine
import com.example.ai.PrescriptionScannerService
import com.example.ai.ScanResult
import com.example.ai.SyntheticPrescriptionGenerator
import com.example.data.AppDatabase
import com.example.data.DoseBuddyRepository
import com.example.data.ScheduleTimes
import com.example.data.SettingsRepository
import com.example.data.model.DoseRecordEntity
import com.example.data.model.MedicineEntity
import com.example.notifications.AlarmScheduler
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.ai.ChatMessage
import com.example.ai.ImageQualityEvaluator
import com.example.ai.ImageQualityReport
import com.example.ai.MedicineChatService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ScreenTab {
    HOME,
    SCANNER,
    MEDICINES,
    INFO,
    HISTORY,
    SETTINGS
}

data class ScannerUiState(
    val selectedPresetId: Int? = null,
    val selectedImageUri: Uri? = null,
    val previewBitmap: Bitmap? = null,
    val sourceLabel: String = "No image selected",
    val isAnalyzing: Boolean = false,
    val analysisStageText: String = "",
    val scanError: String? = null,
    val isAiLive: Boolean = false,
    val isSyntheticDemo: Boolean = false,
    val extractedMedicines: List<ExtractedMedicine> = emptyList(),
    val scanNotes: String = "",
    val userConfirmedDisclaimer: Boolean = false,
    val qualityReport: ImageQualityReport? = null,
    val isEvaluatingQuality: Boolean = false
)

typealias MedTrackerViewModel = DoseBuddyViewModel

class DoseBuddyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = DoseBuddyRepository(database.medicineDao(), database.doseRecordDao())
    val settingsRepo = SettingsRepository(application)
    private val scannerService = PrescriptionScannerService(application)
    private val chatService = MedicineChatService()
    private val voiceReminderManager = com.example.voice.VoiceReminderManager(application)

    private val _currentTab = MutableStateFlow(ScreenTab.HOME)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    val scheduleTimes: StateFlow<ScheduleTimes> = settingsRepo.scheduleTimes
    val notificationsEnabled: StateFlow<Boolean> = settingsRepo.notificationsEnabled
    val soundEnabled: StateFlow<Boolean> = settingsRepo.soundEnabled
    val voiceRemindersEnabled: StateFlow<Boolean> = settingsRepo.voiceRemindersEnabled
    val themeMode: StateFlow<String> = settingsRepo.themeMode
    val languageCode: StateFlow<String> = settingsRepo.languageCode


    private val _isAppReady = MutableStateFlow(false)
    val isAppReady: StateFlow<Boolean> = _isAppReady.asStateFlow()

    private val _isDatabaseOperating = MutableStateFlow(false)
    val isDatabaseOperating: StateFlow<Boolean> = _isDatabaseOperating.asStateFlow()

    val todayDateString: String = DoseBuddyRepository.getTodayDateString()
    val todayFormattedDate: String = DoseBuddyRepository.getTodayFormattedDate()

    val todayDoses: StateFlow<List<DoseRecordEntity>> = repository.getDosesForDate(todayDateString)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMedicines: StateFlow<List<MedicineEntity>> = repository.allMedicines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDoseHistory: StateFlow<List<DoseRecordEntity>> = repository.allDoseHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scannerState = MutableStateFlow(ScannerUiState())
    val scannerState: StateFlow<ScannerUiState> = _scannerState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "Hello! I am your MedTracker Clinical Pharmacotherapy Assistant. I can explain medication uses, mechanisms, common side effects, and clinical precautions for your prescribed medications.\n\nTap any medication button above or type your question below. (Note: Being prescribed a medication does not confirm a personal diagnosis. Never alter or discontinue medications without doctor consultation.)",
                isFromUser = false
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        // Initialize notification channel
        NotificationHelper.createNotificationChannels(application)

        // Seed initial demo data if first launch
        viewModelScope.launch {
            if (!settingsRepo.hasSeededDemo()) {
                repository.seedDemoFictionalData(scheduleTimes.value)
                settingsRepo.setSeededDemo(true)
            } else {
                repository.ensureTodayDoses(todayDateString, scheduleTimes.value)
            }
            _isAppReady.value = true
        }
    }

    fun navigateTo(tab: ScreenTab) {
        _currentTab.value = tab
    }

    fun setThemeMode(mode: String) {
        settingsRepo.setThemeMode(mode)
    }

    fun setLanguageCode(code: String) {
        settingsRepo.setLanguageCode(code)
    }

    fun setSoundEnabled(enabled: Boolean) {
        settingsRepo.setSoundEnabled(enabled)
    }

    fun setVoiceRemindersEnabled(enabled: Boolean) {
        settingsRepo.setVoiceRemindersEnabled(enabled)
        if (enabled) {
            _userMessage.value = "Voice reminders enabled. Spoken notifications will announce scheduled doses when the app is active."
        } else {
            voiceReminderManager.stop()
            _userMessage.value = "Voice reminders disabled."
        }
    }

    fun testVoiceReminder() {
        voiceReminderManager.speakDoseReminder(
            medicineName = "Amoxicillin",
            strength = "500 mg",
            mealInstruction = "After food",
            slot = "MORNING",
            languageCode = languageCode.value,
            isTest = true
        ) { success, msg ->
            _userMessage.value = if (success) "🔊 Spoken reminder: $msg" else "Voice notice: $msg"
        }
    }

    fun speakReminderForDose(dose: DoseRecordEntity) {
        if (!voiceRemindersEnabled.value) return
        voiceReminderManager.speakDoseReminder(
            medicineName = dose.medicineName,
            strength = dose.medicineStrength,
            mealInstruction = dose.mealInstruction,
            slot = dose.slot,
            languageCode = languageCode.value,
            isTest = false
        )
    }

    // --- Chat Actions ---
    fun sendChatMessage(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || _isChatLoading.value) return

        val userMsg = ChatMessage(text = trimmed, isFromUser = true)
        _chatMessages.value = _chatMessages.value + userMsg
        _isChatLoading.value = true

        viewModelScope.launch {
            try {
                val activeNames = allMedicines.value.map { it.name }
                val reply = chatService.askMedicineQuestion(trimmed, activeNames)
                val assistantMsg = ChatMessage(text = reply, isFromUser = false)
                _chatMessages.value = _chatMessages.value + assistantMsg
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    text = "Unable to complete clinical inquiry: ${e.message ?: "Unknown error"}. Please check network connection.",
                    isFromUser = false,
                    isError = true
                )
                _chatMessages.value = _chatMessages.value + errorMsg
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    // --- Dose Actions ---
    fun markDoseTaken(dose: DoseRecordEntity) {
        viewModelScope.launch {
            repository.markDoseTaken(dose.id)
            // Fetch updated medicine info to check remaining stock
            val med = allMedicines.value.firstOrNull { it.id == dose.medicineId }
            val remainingStock = if (med != null && med.doseQuantity > 0) {
                (med.currentQuantity - med.doseQuantity).coerceAtLeast(0)
            } else null

            val lowStockWarning = if (med != null && med.refillAlertEnabled && remainingStock != null && remainingStock <= med.lowStockThreshold) {
                " ⚠️ Low stock: $remainingStock ${med.quantityUnit} left!"
            } else ""

            _userMessage.value = "✓ Marked ${dose.medicineName} as Taken.$lowStockWarning"
        }
    }

    fun markDoseSkipped(dose: DoseRecordEntity) {
        viewModelScope.launch {
            repository.markDoseSkipped(dose.id)
            _userMessage.value = "Marked ${dose.medicineName} as Skipped"
        }
    }

    fun undoDose(dose: DoseRecordEntity) {
        viewModelScope.launch {
            repository.undoDose(dose.id)
            _userMessage.value = "Reset ${dose.medicineName} to Pending (inventory restored)"
        }
    }

    fun cancelSnooze(dose: DoseRecordEntity) {
        viewModelScope.launch {
            repository.clearDoseSnooze(dose.id)
            AlarmScheduler.cancelSnooze(getApplication(), dose.id)
            _userMessage.value = "Snooze cancelled for ${dose.medicineName}"
        }
    }

    fun updateMedicineStock(
        medicineId: Long,
        quantity: Int,
        unit: String,
        doseQty: Int,
        threshold: Int,
        alertEnabled: Boolean
    ) {
        viewModelScope.launch {
            repository.updateMedicineStock(medicineId, quantity, unit, doseQty, threshold, alertEnabled)
            _userMessage.value = "✓ Updated inventory stock details"
        }
    }


    // --- Scanner Actions ---
    fun selectPreset(presetId: Int) {
        val doc = if (presetId == 2) {
            SyntheticPrescriptionGenerator.PRESET_2
        } else {
            SyntheticPrescriptionGenerator.PRESET_1
        }
        val bitmap = SyntheticPrescriptionGenerator.createPrescriptionBitmap(doc)
        _scannerState.value = _scannerState.value.copy(
            selectedPresetId = presetId,
            selectedImageUri = null,
            previewBitmap = bitmap,
            sourceLabel = "Synthetic Demonstration Rx: ${doc.title}",
            extractedMedicines = emptyList(),
            scanError = null,
            isSyntheticDemo = true,
            userConfirmedDisclaimer = false,
            isEvaluatingQuality = false,
            qualityReport = ImageQualityReport(
                isBlurry = false,
                isLowLight = false,
                hasShadows = false,
                hasPoorContrast = false,
                isLowResolution = false,
                isPotentiallyCropped = false,
                isRotatedFixed = false,
                warnings = listOf("Demonstration Prescription: Standard clean synthetic clinical document."),
                suggestions = listOf("Ready for extraction test with Gemini AI model."),
                overallReadability = "Excellent (Synthetic Demo)"
            )
        )
    }

    fun selectCustomImage(uri: Uri?, isCameraCapture: Boolean = false) {
        if (uri == null) return
        _scannerState.value = _scannerState.value.copy(
            selectedPresetId = null,
            selectedImageUri = uri,
            previewBitmap = null,
            sourceLabel = if (isCameraCapture) "Camera Photo Capture" else "Gallery Photo Upload",
            extractedMedicines = emptyList(),
            scanError = null,
            isSyntheticDemo = false,
            userConfirmedDisclaimer = false,
            isEvaluatingQuality = true,
            qualityReport = null
        )
        viewModelScope.launch {
            try {
                val orientedResult = PrescriptionScannerService.loadAndOrientBitmapWithRotation(getApplication(), uri)
                val report = try {
                    ImageQualityEvaluator.evaluateImage(orientedResult.bitmap, orientedResult.wasRotated)
                } catch (_: Throwable) {
                    null
                }
                _scannerState.value = _scannerState.value.copy(
                    previewBitmap = report?.enhancedBitmap ?: orientedResult.bitmap,
                    qualityReport = report,
                    isEvaluatingQuality = false
                )
            } catch (t: Throwable) {
                _scannerState.value = _scannerState.value.copy(
                    previewBitmap = null,
                    sourceLabel = if (isCameraCapture) "Camera Photo Capture" else "Gallery Photo Upload",
                    scanError = "Failed to load selected photo: ${t.message ?: "Invalid image data"}",
                    isEvaluatingQuality = false
                )
            }
        }
    }

    fun runScan() {
        val currentState = _scannerState.value
        if (currentState.previewBitmap == null && currentState.selectedImageUri == null && currentState.selectedPresetId == null) {
            _scannerState.value = currentState.copy(
                scanError = "No prescription image selected. Please take a photo, choose from gallery, or select a demo prescription."
            )
            return
        }

        _scannerState.value = currentState.copy(
            isAnalyzing = true,
            analysisStageText = "Connecting to Gemini Vision AI...",
            scanError = null
        )

        viewModelScope.launch {
            try {
                val isDemo = currentState.selectedPresetId != null && currentState.selectedImageUri == null

                val result = scannerService.analyzePrescription(
                    imageUri = currentState.selectedImageUri,
                    cachedBitmap = currentState.previewBitmap,
                    syntheticPresetId = currentState.selectedPresetId,
                    forceSyntheticPreset = isDemo
                )

                when (result) {
                    is ScanResult.Success -> {
                        _scannerState.value = _scannerState.value.copy(
                            isAnalyzing = false,
                            analysisStageText = "",
                            extractedMedicines = result.medicines,
                            isAiLive = result.isAiLive,
                            isSyntheticDemo = result.isSyntheticDemo,
                            scanNotes = result.rawAiNotes,
                            scanError = null,
                            userConfirmedDisclaimer = false
                        )
                        _userMessage.value = "Prescription analyzed: ${result.medicines.size} medications found. Please review below."
                    }
                    is ScanResult.Error -> {
                        _scannerState.value = _scannerState.value.copy(
                            isAnalyzing = false,
                            analysisStageText = "",
                            scanError = result.message
                        )
                        _userMessage.value = "Prescription extraction failed: ${result.message}"
                    }
                }
            } catch (t: Throwable) {
                _scannerState.value = _scannerState.value.copy(
                    isAnalyzing = false,
                    analysisStageText = "",
                    scanError = "Prescription extraction encountered an issue: ${t.message ?: "Failed to process image"}. Please try again."
                )
            }
        }
    }

    fun setUserConfirmedDisclaimer(confirmed: Boolean) {
        _scannerState.value = _scannerState.value.copy(userConfirmedDisclaimer = confirmed)
    }

    fun updateExtractedMedicine(updated: ExtractedMedicine) {
        val currentList = _scannerState.value.extractedMedicines.toMutableList()
        val index = currentList.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            currentList[index] = updated
            _scannerState.value = _scannerState.value.copy(extractedMedicines = currentList)
        }
    }

    fun removeExtractedMedicine(id: String) {
        val currentList = _scannerState.value.extractedMedicines.filterNot { it.id == id }
        _scannerState.value = _scannerState.value.copy(extractedMedicines = currentList)
    }

    fun confirmAndAddToSchedule() {
        val medicinesToSave = _scannerState.value.extractedMedicines
        if (medicinesToSave.isEmpty()) return

        if (!_scannerState.value.userConfirmedDisclaimer) {
            _userMessage.value = "Please confirm that you have verified the prescription details."
            return
        }

        // Validate that all extracted medicines have at least one reminder notification time configured
        val unconfigured = medicinesToSave.filter { it.reminderTimes.isEmpty() }
        if (unconfigured.isNotEmpty()) {
            _userMessage.value = "Please configure reminder notification time(s) for ${unconfigured.first().name} before saving schedule."
            return
        }

        val hasInvalidTimes = medicinesToSave.any { med ->
            med.reminderTimes.any { !com.example.data.model.TimeSlot.isTimeInAnyValidSlot(it) }
        }
        if (hasInvalidTimes) {
            _userMessage.value = "Some reminder times are outside permitted time slots. Please select valid times."
            return
        }

        if (_isDatabaseOperating.value) return
        _isDatabaseOperating.value = true

        viewModelScope.launch {
            try {
                val times = scheduleTimes.value
                medicinesToSave.forEach { ext ->
                    val reminderTimesStr = ext.reminderTimes.joinToString(", ")
                    val entity = MedicineEntity(
                        name = ext.name.trim(),
                        strength = ext.strength.trim(),
                        form = ext.form,
                        instructions = ext.dosageInstructions.trim().ifEmpty { "Take as directed" },
                        frequency = ext.originalFrequency.trim().ifEmpty { "Once daily" },
                        originalFrequency = ext.originalFrequency.trim(),
                        timingInstructions = ext.timingInstructions.trim(),
                        additionalInstructions = ext.additionalInstructions.trim(),
                        morningSlot = ext.morningSlot,
                        afternoonSlot = ext.afternoonSlot,
                        nightSlot = ext.nightSlot,
                        durationDays = ext.durationDays,
                        isUncertain = ext.isUncertain,
                        uncertaintyReason = ext.uncertaintyReason,
                        mealInstruction = ext.mealInstruction,
                        mealInstructionRaw = ext.mealInstructionRaw,
                        mealInstructionFlagged = ext.mealInstructionFlagged,
                        currentQuantity = ext.currentQuantity,
                        quantityUnit = ext.quantityUnit,
                        doseQuantity = ext.doseQuantity,
                        lowStockThreshold = ext.lowStockThreshold,
                        refillAlertEnabled = ext.refillAlertEnabled,
                        reminderTimes = reminderTimesStr
                    )
                    val medicineId = repository.addConfirmedMedicine(entity, times)

                    // Schedule notification reminder alarms for each configured time
                    ext.reminderTimes.forEach { timeStr ->
                        AlarmScheduler.scheduleMedicineReminder(
                            context = getApplication(),
                            medicineId = medicineId,
                            medicineName = entity.name,
                            dosage = entity.instructions,
                            mealInstruction = entity.mealInstruction,
                            timeString = timeStr
                        )
                    }
                }

                // Clear scanner state
                _scannerState.value = _scannerState.value.copy(
                    extractedMedicines = emptyList(),
                    userConfirmedDisclaimer = false
                )
                _userMessage.value = "✓ Prescription confirmed! Added ${medicinesToSave.size} medications with notification reminders."
                // Navigate back to Home to see the new doses
                _currentTab.value = ScreenTab.HOME
            } catch (e: Exception) {
                _userMessage.value = "Failed to add medications: ${e.message}"
            } finally {
                _isDatabaseOperating.value = false
            }
        }
    }

    // --- Manual Medicine Addition ---
    fun addManualMedicine(medicine: MedicineEntity) {
        val times = medicine.getReminderTimesList()
        if (times.isEmpty() || times.any { !com.example.data.model.TimeSlot.isTimeInAnyValidSlot(it) }) {
            _userMessage.value = "Please configure valid reminder notification time(s) within allowed time slots."
            return
        }

        if (_isDatabaseOperating.value) return
        _isDatabaseOperating.value = true
        viewModelScope.launch {
            try {
                val medicineId = repository.addConfirmedMedicine(medicine, scheduleTimes.value)
                times.forEach { timeStr ->
                    AlarmScheduler.scheduleMedicineReminder(
                        context = getApplication(),
                        medicineId = medicineId,
                        medicineName = medicine.name,
                        dosage = medicine.instructions,
                        mealInstruction = medicine.mealInstruction,
                        timeString = timeStr
                    )
                }
                _userMessage.value = "✓ Added ${medicine.name} to schedule with ${times.size} reminder time(s)."
            } catch (e: Exception) {
                _userMessage.value = "Failed to add ${medicine.name}: ${e.message}"
            } finally {
                _isDatabaseOperating.value = false
            }
        }
    }

    fun updateMedicine(oldMedicine: MedicineEntity, updatedMedicine: MedicineEntity) {
        val newConfiguredTimes = updatedMedicine.getReminderTimesList()
        if (newConfiguredTimes.isEmpty() || newConfiguredTimes.any { !com.example.data.model.TimeSlot.isTimeInAnyValidSlot(it) }) {
            _userMessage.value = "Please configure valid reminder notification time(s) within allowed time slots."
            return
        }

        if (_isDatabaseOperating.value) return
        _isDatabaseOperating.value = true
        viewModelScope.launch {
            try {
                // 1. Cancel previous alarms for removed or changed times
                val oldTimes = oldMedicine.getEffectiveReminderTimes(
                    scheduleTimes.value.morningTime,
                    scheduleTimes.value.afternoonTime,
                    scheduleTimes.value.nightTime
                )
                AlarmScheduler.cancelAllRemindersForMedicine(getApplication(), oldMedicine.id, oldTimes)

                // 2. Persist updated medicine and adjust today's scheduled doses
                repository.updateMedicine(updatedMedicine, scheduleTimes.value)

                // 3. Schedule alarms for all current configured reminder times
                val newTimes = updatedMedicine.getEffectiveReminderTimes(
                    scheduleTimes.value.morningTime,
                    scheduleTimes.value.afternoonTime,
                    scheduleTimes.value.nightTime
                )
                newTimes.forEach { timeStr ->
                    AlarmScheduler.scheduleMedicineReminder(
                        context = getApplication(),
                        medicineId = updatedMedicine.id,
                        medicineName = updatedMedicine.name,
                        dosage = updatedMedicine.instructions,
                        mealInstruction = updatedMedicine.mealInstruction,
                        timeString = timeStr
                    )
                }
                _userMessage.value = "✓ Updated ${updatedMedicine.name} schedule & reminders (${newTimes.size} time(s))."
            } catch (e: Exception) {
                _userMessage.value = "Failed to update ${updatedMedicine.name}: ${e.message}"
            } finally {
                _isDatabaseOperating.value = false
            }
        }
    }

    fun deleteMedicine(medicineId: Long) {
        if (_isDatabaseOperating.value) return
        _isDatabaseOperating.value = true
        viewModelScope.launch {
            try {
                val med = repository.getMedicineById(medicineId)
                if (med != null) {
                    val times = med.getEffectiveReminderTimes(
                        scheduleTimes.value.morningTime,
                        scheduleTimes.value.afternoonTime,
                        scheduleTimes.value.nightTime
                    )
                    AlarmScheduler.cancelAllRemindersForMedicine(getApplication(), medicineId, times)
                }
                repository.deleteMedicine(medicineId)
                _userMessage.value = "Medicine and reminders removed from schedule."
            } catch (e: Exception) {
                _userMessage.value = "Failed to remove medicine: ${e.message}"
            } finally {
                _isDatabaseOperating.value = false
            }
        }
    }

    // --- Settings & Tests ---
    fun updateScheduleTimes(morning: String, afternoon: String, night: String) {
        settingsRepo.updateTimes(morning, afternoon, night)
        _userMessage.value = "Demonstration reminder times updated."
    }

    fun triggerTestNotification() {
        try {
            NotificationHelper.showTestReminder(getApplication(), soundEnabled = soundEnabled.value)
            _userMessage.value = "Test notification sent! Check your notification tray."
        } catch (e: Exception) {
            _userMessage.value = "Could not send test reminder: ${e.message}"
        }
    }

    fun resetDemoData() {
        if (_isDatabaseOperating.value) return
        _isDatabaseOperating.value = true
        viewModelScope.launch {
            try {
                repository.seedDemoFictionalData(scheduleTimes.value)
                _userMessage.value = "Reset to demonstration prescription data."
            } catch (e: Exception) {
                _userMessage.value = "Failed to reset demo data: ${e.message}"
            } finally {
                _isDatabaseOperating.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceReminderManager.shutdown()
    }
}

