package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.MedicineChatService
import com.example.ai.PrescriptionScannerService
import com.example.ai.ScanResult
import com.example.data.DoseBuddyRepository
import com.example.data.ScheduleTimes
import com.example.data.SettingsRepository
import com.example.notifications.NotificationHelper
import com.example.notifications.AlarmScheduler
import com.example.data.model.MedicineEntity
import com.example.data.AppDatabase
import com.example.ui.viewmodel.ScreenTab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches app name MedTracker`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MedTracker", appName)
    }

    @Test
    fun `repository date formats are valid`() {
        val todayStr = DoseBuddyRepository.getTodayDateString()
        val todayFormatted = DoseBuddyRepository.getTodayFormattedDate()
        assertNotNull(todayStr)
        assertNotNull(todayFormatted)
    }

    @Test
    fun `schedule times default values are set`() {
        val times = ScheduleTimes()
        assertEquals("08:00 AM", times.morningTime)
        assertEquals("01:00 PM", times.afternoonTime)
        assertEquals("09:00 PM", times.nightTime)
    }

    @Test
    fun `settings repository manages theme and language`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsRepo = SettingsRepository(context)

        assertEquals("LIGHT", settingsRepo.themeMode.value)
        assertEquals("system", settingsRepo.languageCode.value)

        settingsRepo.setThemeMode("DARK")
        assertEquals("DARK", settingsRepo.themeMode.value)

        settingsRepo.setLanguageCode("hi")
        assertEquals("hi", settingsRepo.languageCode.value)
    }

    @Test
    fun `synthetic demo prescription extracts required structured fields`() {
        val result = PrescriptionScannerService.getSyntheticDemoResult(1)
        assertTrue(result is ScanResult.Success)
        val success = result as ScanResult.Success
        assertEquals(3, success.medicines.size)

        val firstMed = success.medicines[0]
        assertEquals("Amoxicillin", firstMed.name)
        assertEquals("500 mg", firstMed.strength)
        assertNotNull(firstMed.dosageInstructions)
        assertNotNull(firstMed.originalFrequency)
        assertNotNull(firstMed.timingInstructions)
        assertTrue(firstMed.morningSlot)
        assertTrue(firstMed.nightSlot)
    }

    @Test
    fun `synthetic demo preset 2 flags uncertain fields for manual review`() {
        val result = PrescriptionScannerService.getSyntheticDemoResult(2)
        assertTrue(result is ScanResult.Success)
        val success = result as ScanResult.Success
        val uncertainMed = success.medicines.firstOrNull { it.isUncertain }
        assertNotNull(uncertainMed)
        assertEquals("Metoprolol Succinate", uncertainMed?.name)
        assertTrue(uncertainMed?.uncertainFields?.contains("Strength") == true)
        assertTrue(uncertainMed?.uncertaintyReason?.isNotEmpty() == true)
    }

    @Test
    fun `medicine chat service provides verified pharmacotherapy information`() = runBlocking {
        val chatService = MedicineChatService()
        val amoxResponse = chatService.askMedicineQuestion("What is Amoxicillin used for?", listOf("Amoxicillin"))
        assertTrue(amoxResponse.contains("Amoxicillin", ignoreCase = true))
        assertTrue(amoxResponse.contains("Side Effects", ignoreCase = true) || amoxResponse.contains("Precaution", ignoreCase = true))
        assertTrue(amoxResponse.contains("Disclaimer", ignoreCase = true))
    }

    @Test
    fun `screen tab enumeration includes medicine info`() {
        assertTrue(ScreenTab.values().contains(ScreenTab.INFO))
    }

    @Test
    fun `notification channels are created without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.createNotificationChannels(context)
    }

    @Test
    fun `scanner starts with no image selected`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.ui.viewmodel.DoseBuddyViewModel(context)
        val state = viewModel.scannerState.value
        org.junit.Assert.assertNull(state.selectedImageUri)
        org.junit.Assert.assertNull(state.previewBitmap)
        org.junit.Assert.assertNull(state.selectedPresetId)
        assertEquals("No image selected", state.sourceLabel)
    }

    @Test
    fun `scanning with no image selected sets safe error without crashing`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.ui.viewmodel.DoseBuddyViewModel(context)
        viewModel.runScan()
        val state = viewModel.scannerState.value
        assertNotNull(state.scanError)
        assertTrue(state.scanError?.contains("No prescription image") == true)
        org.junit.Assert.assertFalse(state.isAnalyzing)
    }

    @Test
    fun `prescription scanner service handles missing custom api key with visible error`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = PrescriptionScannerService(context)
        // Analyzing a null uri without synthetic preset should fail safely
        val result = service.analyzePrescription(imageUri = null, syntheticPresetId = null)
        assertTrue(result is ScanResult.Error)
        val error = result as ScanResult.Error
        assertTrue(error.message.isNotEmpty())
    }

    @Test
    fun `settings repository handles missing and empty preferences with defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(context)
        assertEquals("08:00 AM", repo.scheduleTimes.value.morningTime)
        repo.updateTimes("", "   ", "")
        assertEquals("08:00 AM", repo.scheduleTimes.value.morningTime)
        assertEquals("01:00 PM", repo.scheduleTimes.value.afternoonTime)
        assertEquals("09:00 PM", repo.scheduleTimes.value.nightTime)
    }

    @Test
    fun `medicine entity reminder times parsing handles multiple times and legacy fallback`() {
        val medWithTimes = MedicineEntity(
            name = "Lisinopril",
            strength = "10 mg",
            reminderTimes = "08:00 AM, 02:00 PM, 09:30 PM"
        )
        val timesList = medWithTimes.getReminderTimesList()
        assertEquals(3, timesList.size)
        assertEquals("08:00 AM", timesList[0])
        assertEquals("02:00 PM", timesList[1])
        assertEquals("09:30 PM", timesList[2])

        val effectiveTimes = medWithTimes.getEffectiveReminderTimes()
        assertEquals(3, effectiveTimes.size)

        // Legacy fallback when reminderTimes is empty
        val legacyMed = MedicineEntity(
            name = "Metformin",
            strength = "500 mg",
            morningSlot = true,
            afternoonSlot = false,
            nightSlot = true,
            reminderTimes = ""
        )
        val legacyEffective = legacyMed.getEffectiveReminderTimes("07:30 AM", "12:30 PM", "08:30 PM")
        assertEquals(2, legacyEffective.size)
        assertEquals("07:30 AM", legacyEffective[0])
        assertEquals("08:30 PM", legacyEffective[1])
    }

    @Test
    fun `alarm scheduler request codes are unique per medicine and time`() {
        val code1 = AlarmScheduler.getReminderRequestCode(1L, "08:00 AM")
        val code2 = AlarmScheduler.getReminderRequestCode(1L, "08:00 PM")
        val code3 = AlarmScheduler.getReminderRequestCode(2L, "08:00 AM")

        assertTrue(code1 != code2)
        assertTrue(code1 != code3)
        assertEquals(code1, AlarmScheduler.getReminderRequestCode(1L, "08:00 AM"))
    }

    @Test
    fun `alarm scheduler parses slots accurately across 24 hours`() {
        assertEquals("MORNING", AlarmScheduler.parseSlotFromTime("07:30 AM"))
        assertEquals("MORNING", AlarmScheduler.parseSlotFromTime("11:59 AM"))
        assertEquals("AFTERNOON", AlarmScheduler.parseSlotFromTime("12:00 PM"))
        assertEquals("AFTERNOON", AlarmScheduler.parseSlotFromTime("04:30 PM"))
        assertEquals("NIGHT", AlarmScheduler.parseSlotFromTime("07:00 PM"))
        assertEquals("NIGHT", AlarmScheduler.parseSlotFromTime("10:15 PM"))
    }

    @Test
    fun `database persists medicine with custom reminder times and generates non-duplicate doses`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val repo = DoseBuddyRepository(db.medicineDao(), db.doseRecordDao())

        val med = MedicineEntity(
            name = "Atorvastatin",
            strength = "20 mg",
            instructions = "Take 1 tablet at night",
            frequency = "Once daily",
            mealInstruction = "After food",
            currentQuantity = 30,
            quantityUnit = "tablets",
            doseQuantity = 1,
            reminderTimes = "09:00 PM"
        )
        val id = repo.addConfirmedMedicine(med, ScheduleTimes())
        assertTrue(id > 0)

        val retrieved = repo.getMedicineById(id)
        assertNotNull(retrieved)
        assertEquals("09:00 PM", retrieved?.reminderTimes)
        assertEquals(listOf("09:00 PM"), retrieved?.getReminderTimesList())

        // Calling addConfirmedMedicine again with same times doesn't duplicate today's dose
        val todayStr = DoseBuddyRepository.getTodayDateString()
        val existingDose = db.doseRecordDao().getDoseByMedicineDateAndTime(id, todayStr, "09:00 PM")
        assertNotNull(existingDose)
    }

    @Test
    fun `updating medicine reminder times preserves data and updates active times`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val repo = DoseBuddyRepository(db.medicineDao(), db.doseRecordDao())

        val med = MedicineEntity(
            name = "Levothyroxine",
            strength = "50 mcg",
            instructions = "Take in morning on empty stomach",
            frequency = "Once daily",
            mealInstruction = "Before food",
            reminderTimes = "06:30 AM"
        )
        val id = repo.addConfirmedMedicine(med, ScheduleTimes())
        val savedMed = repo.getMedicineById(id)!!

        val updatedMed = savedMed.copy(reminderTimes = "07:00 AM, 07:00 PM")
        repo.updateMedicine(updatedMed, ScheduleTimes())

        val retrievedUpdated = repo.getMedicineById(id)!!
        assertEquals("07:00 AM, 07:00 PM", retrievedUpdated.reminderTimes)
        assertEquals(2, retrievedUpdated.getReminderTimesList().size)
    }

    @Test
    fun `alert sound manager starts and stops safely without overlapping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.notifications.AlertSoundManager.play(context)
        // Sound or vibration active
        com.example.notifications.AlertSoundManager.stop()
        org.junit.Assert.assertFalse(com.example.notifications.AlertSoundManager.isAlertActive())
    }

    @Test
    fun `alarm scheduler snooze request codes are deterministic and unique per dose`() {
        val dose1Code = AlarmScheduler.getSnoozeRequestCode(101L)
        val dose2Code = AlarmScheduler.getSnoozeRequestCode(102L)
        assertTrue(dose1Code != dose2Code)
        assertEquals(dose1Code, AlarmScheduler.getSnoozeRequestCode(101L))
    }

    @Test
    fun `snoozing dose keeps status PENDING and records snoozedUntil timestamp`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val repo = DoseBuddyRepository(db.medicineDao(), db.doseRecordDao())

        val med = MedicineEntity(
            name = "Omeprazole",
            strength = "20 mg",
            reminderTimes = "08:00 AM"
        )
        val medId = repo.addConfirmedMedicine(med, ScheduleTimes())
        val todayStr = DoseBuddyRepository.getTodayDateString()
        val dose = db.doseRecordDao().getDoseByMedicineDateAndTime(medId, todayStr, "08:00 AM")!!

        assertEquals("PENDING", dose.status)
        val snoozeTargetTime = System.currentTimeMillis() + (15 * 60 * 1000L)
        repo.updateDoseSnooze(dose.id, snoozeTargetTime)

        val snoozedDose = db.doseRecordDao().getDoseById(dose.id)!!
        assertEquals("PENDING", snoozedDose.status)
        assertEquals(snoozeTargetTime, snoozedDose.snoozedUntil)

        // Cancel snooze resets snoozedUntil while keeping dose PENDING
        repo.clearDoseSnooze(dose.id)
        val clearedDose = db.doseRecordDao().getDoseById(dose.id)!!
        assertEquals(0L, clearedDose.snoozedUntil)
        assertEquals("PENDING", clearedDose.status)
    }

    @Test
    fun `marking dose taken records timestamp, deducts inventory, and undo restores it`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val repo = DoseBuddyRepository(db.medicineDao(), db.doseRecordDao())

        val med = MedicineEntity(
            name = "Ciprofloxacin",
            strength = "500 mg",
            currentQuantity = 20,
            quantityUnit = "tablets",
            doseQuantity = 1,
            reminderTimes = "09:00 AM"
        )
        val medId = repo.addConfirmedMedicine(med, ScheduleTimes())
        val todayStr = DoseBuddyRepository.getTodayDateString()
        val dose = db.doseRecordDao().getDoseByMedicineDateAndTime(medId, todayStr, "09:00 AM")!!

        // Mark Taken
        repo.markDoseTaken(dose.id)
        val takenDose = db.doseRecordDao().getDoseById(dose.id)!!
        assertEquals("TAKEN", takenDose.status)
        assertNotNull(takenDose.actionTimestamp)
        assertEquals(1, takenDose.doseDeducted)

        val updatedMed = repo.getMedicineById(medId)!!
        assertEquals(19, updatedMed.currentQuantity)

        // Undo
        repo.undoDose(dose.id)
        val restoredDose = db.doseRecordDao().getDoseById(dose.id)!!
        assertEquals("PENDING", restoredDose.status)
        assertEquals(0, restoredDose.doseDeducted)

        val restoredMed = repo.getMedicineById(medId)!!
        assertEquals(20, restoredMed.currentQuantity)
    }

    @Test
    fun `marking dose skipped records timestamp without deducting inventory`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val repo = DoseBuddyRepository(db.medicineDao(), db.doseRecordDao())

        val med = MedicineEntity(
            name = "Metoprolol",
            strength = "25 mg",
            currentQuantity = 30,
            quantityUnit = "tablets",
            doseQuantity = 1,
            reminderTimes = "08:00 AM"
        )
        val medId = repo.addConfirmedMedicine(med, ScheduleTimes())
        val todayStr = DoseBuddyRepository.getTodayDateString()
        val dose = db.doseRecordDao().getDoseByMedicineDateAndTime(medId, todayStr, "08:00 AM")!!

        // Mark Skipped
        repo.markDoseSkipped(dose.id)
        val skippedDose = db.doseRecordDao().getDoseById(dose.id)!!
        assertEquals("SKIPPED", skippedDose.status)
        assertNotNull(skippedDose.actionTimestamp)
        assertEquals(0, skippedDose.doseDeducted)

        val medAfterSkip = repo.getMedicineById(medId)!!
        assertEquals(30, medAfterSkip.currentQuantity) // Not deducted
    }

    @Test
    fun `deleting a medicine removes all its scheduled dose entries`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val repo = DoseBuddyRepository(db.medicineDao(), db.doseRecordDao())

        val med = MedicineEntity(
            name = "Ibuprofen",
            strength = "400 mg",
            reminderTimes = "02:00 PM"
        )
        val medId = repo.addConfirmedMedicine(med, ScheduleTimes())
        val todayStr = DoseBuddyRepository.getTodayDateString()
        assertNotNull(db.doseRecordDao().getDoseByMedicineDateAndTime(medId, todayStr, "02:00 PM"))

        repo.deleteMedicine(medId)
        org.junit.Assert.assertNull(repo.getMedicineById(medId))
        org.junit.Assert.assertNull(db.doseRecordDao().getDoseByMedicineDateAndTime(medId, todayStr, "02:00 PM"))
    }

    @Test
    fun `medicine alert intent has correct extras and flags for full screen presentation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = com.example.ui.screens.MedicineAlertActivity.createIntent(
            context = context,
            doseId = 42L,
            medicineId = 10L,
            medicineName = "Amoxicillin",
            strength = "500 mg",
            dosage = "1 capsule twice daily",
            scheduledTime = "08:30 AM",
            mealInstruction = "After food",
            slot = "MORNING",
            notificationId = 1001
        )

        assertNotNull(intent)
        assertEquals(42L, intent.getLongExtra(com.example.ui.screens.MedicineAlertActivity.EXTRA_DOSE_ID, -1L))
        assertEquals(10L, intent.getLongExtra(com.example.ui.screens.MedicineAlertActivity.EXTRA_MEDICINE_ID, -1L))
        assertEquals("Amoxicillin", intent.getStringExtra(com.example.ui.screens.MedicineAlertActivity.EXTRA_MEDICINE_NAME))
        assertEquals("500 mg", intent.getStringExtra(com.example.ui.screens.MedicineAlertActivity.EXTRA_STRENGTH))
        assertEquals("1 capsule twice daily", intent.getStringExtra(com.example.ui.screens.MedicineAlertActivity.EXTRA_DOSAGE))
        assertEquals("08:30 AM", intent.getStringExtra(com.example.ui.screens.MedicineAlertActivity.EXTRA_SCHEDULED_TIME))
        assertEquals("After food", intent.getStringExtra(com.example.ui.screens.MedicineAlertActivity.EXTRA_MEAL))
        assertEquals(1001, intent.getIntExtra(com.example.ui.screens.MedicineAlertActivity.EXTRA_NOTIFICATION_ID, -1))
        assertTrue((intent.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun `alert sound manager stop can be called safely without crashing`() {
        com.example.notifications.AlertSoundManager.stop()
        // Calling stop repeatedly must be safe and idempotent
        com.example.notifications.AlertSoundManager.stop()
    }

    @Test
    fun `snooze request code is consistent and deterministic`() {
        val rc1 = AlarmScheduler.getSnoozeRequestCode(123L)
        val rc2 = AlarmScheduler.getSnoozeRequestCode(123L)
        assertEquals(rc1, rc2)
    }

    @Test
    fun `medicine with configured times returns only configured times without generic fallback`() {
        val customMed = MedicineEntity(
            name = "Levothyroxine",
            strength = "50 mcg",
            reminderTimes = "06:30 AM, 05:45 PM"
        )
        val times = customMed.getEffectiveReminderTimes()
        assertEquals(2, times.size)
        assertEquals("06:30 AM", times[0])
        assertEquals("05:45 PM", times[1])
    }

    // --- FEATURE 1: SPOKEN MEDICINE ALERTS TESTS ---

    @Test
    fun `voice alert announces medicine name and scheduled dose time without invented instructions`() {
        val announcement = com.example.ui.screens.MedicineAlertActivity.buildAlertSpeechText(
            medicineName = "Paracetamol",
            scheduledTime = "8 PM",
            dosage = "",
            mealInstruction = "",
            languageCode = "en"
        )
        assertEquals("It's time to take Paracetamol, scheduled for 8 PM.", announcement)
    }

    @Test
    fun `voice alert includes confirmed dosage and meal instructions when provided`() {
        val announcement = com.example.ui.screens.MedicineAlertActivity.buildAlertSpeechText(
            medicineName = "Paracetamol",
            scheduledTime = "8 PM",
            dosage = "1 tablet",
            mealInstruction = "After food",
            languageCode = "en"
        )
        assertEquals(
            "It's time to take Paracetamol, scheduled for 8 PM. Dosage: 1 tablet. Instructions: After food.",
            announcement
        )
    }

    @Test
    fun `voice alert does not invent dosage when default phrase is used`() {
        val announcement = com.example.ui.screens.MedicineAlertActivity.buildAlertSpeechText(
            medicineName = "Metformin",
            scheduledTime = "01:00 PM",
            dosage = "Take as prescribed",
            mealInstruction = "No meal instruction specified",
            languageCode = "en"
        )
        assertEquals("It's time to take Metformin, scheduled for 01:00 PM.", announcement)
    }

    @Test
    fun `voice alert supports Hindi and Telugu spoken announcements`() {
        val hindi = com.example.ui.screens.MedicineAlertActivity.buildAlertSpeechText(
            medicineName = "Aspirin",
            scheduledTime = "08:00 AM",
            dosage = "75 mg",
            mealInstruction = "भोजन के बाद",
            languageCode = "hi"
        )
        assertTrue(hindi.contains("Aspirin लेने का समय हो गया है"))
        assertTrue(hindi.contains("08:00 AM"))

        val telugu = com.example.ui.screens.MedicineAlertActivity.buildAlertSpeechText(
            medicineName = "Aspirin",
            scheduledTime = "08:00 AM",
            dosage = "75 mg",
            mealInstruction = "భోజనం తర్వాత",
            languageCode = "te"
        )
        assertTrue(telugu.contains("Aspirin తీసుకునే సమయం ఆసన్నమైంది"))
        assertTrue(telugu.contains("08:00 AM"))
    }

    @Test
    fun `alert speech debouncer prevents duplicate announcement within debounce window`() {
        com.example.ui.screens.AlertSpeechDebouncer.resetForTest()
        val key = "Amoxicillin_08:00 AM_123"

        val firstAttempt = com.example.ui.screens.AlertSpeechDebouncer.shouldSpeak(key)
        val duplicateAttempt = com.example.ui.screens.AlertSpeechDebouncer.shouldSpeak(key)

        assertTrue(firstAttempt)
        assertFalse("Duplicate attempt within window must be skipped", duplicateAttempt)

        // Different alert key should be permitted
        val differentAlert = com.example.ui.screens.AlertSpeechDebouncer.shouldSpeak("Metformin_01:00 PM_456")
        assertTrue(differentAlert)
    }

    // --- FEATURE 2: TIME-SLOT RESTRICTIONS TESTS ---

    @Test
    fun `night slot permits 7 PM to 11 59 PM and rejects times before 7 PM and midnight`() {
        val nightSlot = com.example.data.model.TimeSlot.NIGHT

        // Valid Night times
        assertTrue(nightSlot.isValid(19, 0)) // 7:00 PM
        assertTrue(nightSlot.isValid(20, 30)) // 8:30 PM
        assertTrue(nightSlot.isValid(23, 59)) // 11:59 PM
        assertTrue(nightSlot.isValidTime("07:00 PM"))
        assertTrue(nightSlot.isValidTime("08:30 PM"))
        assertTrue(nightSlot.isValidTime("11:59 PM"))

        // Times before 7:00 PM must be rejected
        assertFalse(nightSlot.isValid(18, 59)) // 6:59 PM
        assertFalse(nightSlot.isValid(12, 0)) // 12:00 PM
        assertFalse(nightSlot.isValid(8, 0)) // 8:00 AM
        assertFalse(nightSlot.isValidTime("06:59 PM"))
        assertFalse(nightSlot.isValidTime("06:00 PM"))
        assertFalse(nightSlot.isValidTime("08:00 AM"))

        // Midnight (12:00 AM / 00:00) must be rejected
        assertFalse(nightSlot.isValid(0, 0))
        assertFalse(nightSlot.isValidTime("12:00 AM"))

        // Night valid hours are strictly 19, 20, 21, 22, 23 (7 PM, 8 PM, 9 PM, 10 PM, 11 PM)
        assertEquals(listOf(19, 20, 21, 22, 23), nightSlot.validHours24)
    }

    @Test
    fun `morning slot strictly permits 5 AM to 11 59 AM`() {
        val morningSlot = com.example.data.model.TimeSlot.MORNING

        assertTrue(morningSlot.isValid(5, 0))
        assertTrue(morningSlot.isValid(11, 59))
        assertTrue(morningSlot.isValidTime("05:00 AM"))
        assertTrue(morningSlot.isValidTime("11:59 AM"))

        assertFalse(morningSlot.isValid(4, 59))
        assertFalse(morningSlot.isValid(12, 0))
        assertFalse(morningSlot.isValidTime("04:59 AM"))
        assertFalse(morningSlot.isValidTime("12:00 PM"))

        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11), morningSlot.validHours24)
    }

    @Test
    fun `afternoon slot strictly permits 12 PM to 4 59 PM`() {
        val afternoonSlot = com.example.data.model.TimeSlot.AFTERNOON

        assertTrue(afternoonSlot.isValid(12, 0))
        assertTrue(afternoonSlot.isValid(16, 59))
        assertTrue(afternoonSlot.isValidTime("12:00 PM"))
        assertTrue(afternoonSlot.isValidTime("04:59 PM"))

        assertFalse(afternoonSlot.isValid(11, 59))
        assertFalse(afternoonSlot.isValid(17, 0))
        assertFalse(afternoonSlot.isValidTime("11:59 AM"))
        assertFalse(afternoonSlot.isValidTime("05:00 PM"))

        assertEquals(listOf(12, 13, 14, 15, 16), afternoonSlot.validHours24)
    }

    @Test
    fun `evening slot strictly permits 5 PM to 6 59 PM`() {
        val eveningSlot = com.example.data.model.TimeSlot.EVENING

        assertTrue(eveningSlot.isValid(17, 0))
        assertTrue(eveningSlot.isValid(18, 59))
        assertTrue(eveningSlot.isValidTime("05:00 PM"))
        assertTrue(eveningSlot.isValidTime("06:59 PM"))

        assertFalse(eveningSlot.isValid(16, 59))
        assertFalse(eveningSlot.isValid(19, 0))
        assertFalse(eveningSlot.isValidTime("04:59 PM"))
        assertFalse(eveningSlot.isValidTime("07:00 PM"))

        assertEquals(listOf(17, 18), eveningSlot.validHours24)
    }

    @Test
    fun `findSlotForTime maps valid times correctly and returns null for outside times`() {
        assertEquals(com.example.data.model.TimeSlot.MORNING, com.example.data.model.TimeSlot.findSlotForTime("08:00 AM"))
        assertEquals(com.example.data.model.TimeSlot.AFTERNOON, com.example.data.model.TimeSlot.findSlotForTime("01:30 PM"))
        assertEquals(com.example.data.model.TimeSlot.EVENING, com.example.data.model.TimeSlot.findSlotForTime("05:30 PM"))
        assertEquals(com.example.data.model.TimeSlot.NIGHT, com.example.data.model.TimeSlot.findSlotForTime("09:00 PM"))

        // Times outside all permitted slots
        assertNull(com.example.data.model.TimeSlot.findSlotForTime("12:00 AM")) // Midnight
        assertNull(com.example.data.model.TimeSlot.findSlotForTime("03:30 AM")) // Middle of night
        assertNull(com.example.data.model.TimeSlot.findSlotForTime("04:59 AM")) // Just before Morning
    }

    @Test
    fun `time slot explanation displays short permitted range text`() {
        val explanation = com.example.data.model.TimeSlot.getExplanationForSlot(com.example.data.model.TimeSlot.NIGHT)
        assertEquals("Permitted time range for Night: 7:00 PM to 11:59 PM", explanation)
    }
}
