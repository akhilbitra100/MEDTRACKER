package com.example.data

import com.example.data.dao.DoseRecordDao
import com.example.data.dao.MedicineDao
import com.example.data.model.DoseRecordEntity
import com.example.data.model.MedicineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

typealias MedTrackerRepository = DoseBuddyRepository

class DoseBuddyRepository(
    private val medicineDao: MedicineDao,
    private val doseRecordDao: DoseRecordDao
) {
    val allMedicines: Flow<List<MedicineEntity>> = medicineDao.getAllMedicines()
    val allDoseHistory: Flow<List<DoseRecordEntity>> = doseRecordDao.getAllDoseRecords()

    fun getDosesForDate(date: String): Flow<List<DoseRecordEntity>> {
        return doseRecordDao.getDosesForDate(date)
    }

    suspend fun addConfirmedMedicine(
        medicine: MedicineEntity,
        scheduleTimes: ScheduleTimes
    ): Long {
        val medicineId = medicineDao.insertMedicine(medicine)
        // Generate today's doses for this newly added medicine based on its configured reminder times
        val todayStr = getTodayDateString()
        val activeTimes = medicine.getEffectiveReminderTimes(
            morningTime = scheduleTimes.morningTime,
            afternoonTime = scheduleTimes.afternoonTime,
            nightTime = scheduleTimes.nightTime
        )
        val newDoses = mutableListOf<DoseRecordEntity>()

        for (time in activeTimes) {
            val existing = doseRecordDao.getDoseByMedicineDateAndTime(medicineId, todayStr, time)
            if (existing == null) {
                val slot = com.example.notifications.AlarmScheduler.parseSlotFromTime(time)
                newDoses.add(
                    DoseRecordEntity(
                        medicineId = medicineId,
                        medicineName = medicine.name,
                        medicineStrength = medicine.strength,
                        slot = slot,
                        scheduledTime = time,
                        scheduledDate = todayStr,
                        status = "PENDING",
                        mealInstruction = medicine.mealInstruction,
                        mealInstructionRaw = medicine.mealInstructionRaw,
                        notes = medicine.instructions
                    )
                )
            }
        }
        if (newDoses.isNotEmpty()) {
            doseRecordDao.insertDoses(newDoses)
        }
        return medicineId
    }

    suspend fun markDoseTaken(id: Long) {
        val dose = doseRecordDao.getDoseById(id) ?: return
        if (dose.status.equals("TAKEN", ignoreCase = true)) return // Prevent duplicate execution

        val timestamp = System.currentTimeMillis()
        var deducted = dose.doseDeducted

        // Deduct quantity only when marking Taken and not already deducted for this dose
        if (deducted == 0) {
            val med = medicineDao.getMedicineById(dose.medicineId)
            if (med != null && med.doseQuantity > 0 && med.currentQuantity > 0) {
                val deductionAmount = med.doseQuantity.coerceAtMost(med.currentQuantity)
                val newQty = (med.currentQuantity - deductionAmount).coerceAtLeast(0)
                medicineDao.updateMedicineQuantity(med.id, newQty)
                deducted = deductionAmount
            }
        }

        doseRecordDao.updateDoseStatusAndDeduction(id, "TAKEN", timestamp, deducted)
        doseRecordDao.clearDoseSnooze(id)
    }

    suspend fun markDoseSkipped(id: Long) {
        val dose = doseRecordDao.getDoseById(id) ?: return
        // If this dose was previously TAKEN with deducted quantity, restore it
        if (dose.status.equals("TAKEN", ignoreCase = true) && dose.doseDeducted > 0) {
            val med = medicineDao.getMedicineById(dose.medicineId)
            if (med != null) {
                val restoredQty = med.currentQuantity + dose.doseDeducted
                medicineDao.updateMedicineQuantity(med.id, restoredQty)
            }
        }
        val timestamp = System.currentTimeMillis()
        // Skipped doses never deduct medicine units
        doseRecordDao.updateDoseStatusAndDeduction(id, "SKIPPED", timestamp, 0)
        doseRecordDao.clearDoseSnooze(id)
    }

    suspend fun undoDose(id: Long) {
        val dose = doseRecordDao.getDoseById(id) ?: return
        // If this dose was previously TAKEN with deducted quantity, restore it
        if (dose.status.equals("TAKEN", ignoreCase = true) && dose.doseDeducted > 0) {
            val med = medicineDao.getMedicineById(dose.medicineId)
            if (med != null) {
                val restoredQty = med.currentQuantity + dose.doseDeducted
                medicineDao.updateMedicineQuantity(med.id, restoredQty)
            }
        }
        // Pending doses have 0 deductions
        doseRecordDao.updateDoseStatusAndDeduction(id, "PENDING", null, 0)
        doseRecordDao.clearDoseSnooze(id)
    }

    suspend fun updateDoseSnooze(id: Long, snoozedUntil: Long) {
        doseRecordDao.updateDoseSnooze(id, snoozedUntil)
    }

    suspend fun clearDoseSnooze(id: Long) {
        doseRecordDao.clearDoseSnooze(id)
    }

    suspend fun updateDoseStatus(id: Long, status: String) {
        when (status.uppercase()) {
            "TAKEN" -> markDoseTaken(id)
            "SKIPPED" -> markDoseSkipped(id)
            else -> undoDose(id)
        }
    }

    suspend fun updateMedicineStock(
        medicineId: Long,
        quantity: Int,
        unit: String,
        doseQty: Int,
        threshold: Int,
        alertEnabled: Boolean
    ) {
        medicineDao.updateRefillSettings(
            id = medicineId,
            quantity = quantity.coerceAtLeast(0),
            unit = unit.ifBlank { "tablets" },
            doseQty = doseQty.coerceAtLeast(1),
            threshold = threshold.coerceAtLeast(0),
            alertEnabled = alertEnabled
        )
    }

    suspend fun getMedicineById(medicineId: Long): MedicineEntity? {
        return medicineDao.getMedicineById(medicineId)
    }

    suspend fun updateMedicine(
        medicine: MedicineEntity,
        scheduleTimes: ScheduleTimes
    ) {
        medicineDao.updateMedicine(medicine)
        val todayStr = getTodayDateString()
        val activeTimes = medicine.getEffectiveReminderTimes(
            morningTime = scheduleTimes.morningTime,
            afternoonTime = scheduleTimes.afternoonTime,
            nightTime = scheduleTimes.nightTime
        )

        if (activeTimes.isNotEmpty()) {
            doseRecordDao.deleteStaleDosesForMedicineAndDate(medicine.id, todayStr, activeTimes)
        }

        val newDoses = mutableListOf<DoseRecordEntity>()
        for (time in activeTimes) {
            val existing = doseRecordDao.getDoseByMedicineDateAndTime(medicine.id, todayStr, time)
            if (existing == null) {
                val slot = com.example.notifications.AlarmScheduler.parseSlotFromTime(time)
                newDoses.add(
                    DoseRecordEntity(
                        medicineId = medicine.id,
                        medicineName = medicine.name,
                        medicineStrength = medicine.strength,
                        slot = slot,
                        scheduledTime = time,
                        scheduledDate = todayStr,
                        status = "PENDING",
                        mealInstruction = medicine.mealInstruction,
                        mealInstructionRaw = medicine.mealInstructionRaw,
                        notes = medicine.instructions
                    )
                )
            }
        }
        if (newDoses.isNotEmpty()) {
            doseRecordDao.insertDoses(newDoses)
        }
    }

    suspend fun deleteMedicine(medicineId: Long) {
        medicineDao.deleteMedicineById(medicineId)
        doseRecordDao.deleteDosesForMedicine(medicineId)
    }

    suspend fun ensureTodayDoses(date: String, scheduleTimes: ScheduleTimes) {
        val medicines = medicineDao.getAllMedicines().first()
        val newDoses = mutableListOf<DoseRecordEntity>()
        medicines.forEach { med ->
            val activeTimes = med.getEffectiveReminderTimes(
                morningTime = scheduleTimes.morningTime,
                afternoonTime = scheduleTimes.afternoonTime,
                nightTime = scheduleTimes.nightTime
            )
            activeTimes.forEach { time ->
                val existing = doseRecordDao.getDoseByMedicineDateAndTime(med.id, date, time)
                if (existing == null) {
                    val slot = com.example.notifications.AlarmScheduler.parseSlotFromTime(time)
                    newDoses.add(
                        DoseRecordEntity(
                            medicineId = med.id,
                            medicineName = med.name,
                            medicineStrength = med.strength,
                            slot = slot,
                            scheduledTime = time,
                            scheduledDate = date,
                            status = "PENDING",
                            mealInstruction = med.mealInstruction,
                            mealInstructionRaw = med.mealInstructionRaw,
                            notes = med.instructions
                        )
                    )
                }
            }
        }
        if (newDoses.isNotEmpty()) {
            doseRecordDao.insertDoses(newDoses)
        }
    }

    suspend fun seedDemoFictionalData(scheduleTimes: ScheduleTimes) {
        medicineDao.clearAllMedicines()
        doseRecordDao.clearAllDoses()

        val med1 = MedicineEntity(
            name = "Amoxicillin",
            strength = "500 mg",
            form = "Capsule",
            instructions = "Take 1 capsule twice daily with meals (Morning and Night) for 7 days",
            frequency = "Twice daily",
            morningSlot = true,
            afternoonSlot = false,
            nightSlot = true,
            durationDays = 7,
            notes = "Finish entire prescribed course.",
            mealInstruction = "With food",
            mealInstructionRaw = "with meals",
            currentQuantity = 13, // 14 minus 1 taken morning dose
            quantityUnit = "capsules",
            doseQuantity = 1,
            lowStockThreshold = 4,
            refillAlertEnabled = true,
            reminderTimes = "${scheduleTimes.morningTime}, ${scheduleTimes.nightTime}"
        )
        val med2 = MedicineEntity(
            name = "Lisinopril",
            strength = "10 mg",
            form = "Tablet",
            instructions = "Take 1 tablet once daily every morning with water",
            frequency = "Once daily morning",
            morningSlot = true,
            afternoonSlot = false,
            nightSlot = false,
            durationDays = 30,
            notes = "Blood pressure maintenance.",
            mealInstruction = "No meal instruction specified",
            mealInstructionRaw = "",
            currentQuantity = 29, // 30 minus 1 taken morning dose
            quantityUnit = "tablets",
            doseQuantity = 1,
            lowStockThreshold = 5,
            refillAlertEnabled = true,
            reminderTimes = scheduleTimes.morningTime
        )
        val med3 = MedicineEntity(
            name = "Metformin",
            strength = "500 mg",
            form = "Tablet",
            instructions = "Take 1 tablet daily with lunch / afternoon meal",
            frequency = "Once daily afternoon",
            morningSlot = false,
            afternoonSlot = true,
            nightSlot = false,
            durationDays = 30,
            notes = "Take with food to prevent upset stomach.",
            mealInstruction = "With food",
            mealInstructionRaw = "with lunch / afternoon meal",
            currentQuantity = 30,
            quantityUnit = "tablets",
            doseQuantity = 1,
            lowStockThreshold = 5,
            refillAlertEnabled = true,
            reminderTimes = scheduleTimes.afternoonTime
        )

        val id1 = medicineDao.insertMedicine(med1)
        val id2 = medicineDao.insertMedicine(med2)
        val id3 = medicineDao.insertMedicine(med3)

        val today = getTodayDateString()
        val doses = listOf(
            DoseRecordEntity(
                medicineId = id1,
                medicineName = med1.name,
                medicineStrength = med1.strength,
                slot = "MORNING",
                scheduledTime = scheduleTimes.morningTime,
                scheduledDate = today,
                status = "TAKEN",
                actionTimestamp = System.currentTimeMillis() - 7200000L,
                mealInstruction = med1.mealInstruction,
                mealInstructionRaw = med1.mealInstructionRaw,
                doseDeducted = 1
            ),
            DoseRecordEntity(
                medicineId = id2,
                medicineName = med2.name,
                medicineStrength = med2.strength,
                slot = "MORNING",
                scheduledTime = scheduleTimes.morningTime,
                scheduledDate = today,
                status = "TAKEN",
                actionTimestamp = System.currentTimeMillis() - 7100000L,
                mealInstruction = med2.mealInstruction,
                mealInstructionRaw = med2.mealInstructionRaw,
                doseDeducted = 1
            ),
            DoseRecordEntity(
                medicineId = id3,
                medicineName = med3.name,
                medicineStrength = med3.strength,
                slot = "AFTERNOON",
                scheduledTime = scheduleTimes.afternoonTime,
                scheduledDate = today,
                status = "PENDING",
                mealInstruction = med3.mealInstruction,
                mealInstructionRaw = med3.mealInstructionRaw,
                doseDeducted = 0
            ),
            DoseRecordEntity(
                medicineId = id1,
                medicineName = med1.name,
                medicineStrength = med1.strength,
                slot = "NIGHT",
                scheduledTime = scheduleTimes.nightTime,
                scheduledDate = today,
                status = "PENDING",
                mealInstruction = med1.mealInstruction,
                mealInstructionRaw = med1.mealInstructionRaw,
                doseDeducted = 0
            )
        )
        doseRecordDao.insertDoses(doses)
    }

    suspend fun clearAll() {
        medicineDao.clearAllMedicines()
        doseRecordDao.clearAllDoses()
    }

    companion object {
        fun getTodayDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }

        fun getTodayFormattedDate(): String {
            val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}
