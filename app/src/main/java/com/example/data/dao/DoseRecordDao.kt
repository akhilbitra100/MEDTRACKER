package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DoseRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseRecordDao {
    @Query("SELECT * FROM dose_records WHERE scheduledDate = :date ORDER BY id ASC")
    fun getDosesForDate(date: String): Flow<List<DoseRecordEntity>>

    @Query("SELECT * FROM dose_records ORDER BY scheduledDate DESC, id DESC")
    fun getAllDoseRecords(): Flow<List<DoseRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDose(dose: DoseRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoses(doses: List<DoseRecordEntity>): List<Long>

    @Update
    suspend fun updateDose(dose: DoseRecordEntity)

    @Query("SELECT * FROM dose_records WHERE id = :id LIMIT 1")
    suspend fun getDoseById(id: Long): DoseRecordEntity?

    @Query("UPDATE dose_records SET status = :status, actionTimestamp = :timestamp WHERE id = :id")
    suspend fun updateDoseStatus(id: Long, status: String, timestamp: Long?)

    @Query("UPDATE dose_records SET status = :status, actionTimestamp = :timestamp, doseDeducted = :deducted WHERE id = :id")
    suspend fun updateDoseStatusAndDeduction(id: Long, status: String, timestamp: Long?, deducted: Int)

    @Query("UPDATE dose_records SET snoozedUntil = :snoozedUntil WHERE id = :id")
    suspend fun updateDoseSnooze(id: Long, snoozedUntil: Long)

    @Query("UPDATE dose_records SET snoozedUntil = 0 WHERE id = :id")
    suspend fun clearDoseSnooze(id: Long)


    @Query("SELECT * FROM dose_records WHERE medicineId = :medicineId AND scheduledDate = :date AND scheduledTime = :time LIMIT 1")
    suspend fun getDoseByMedicineDateAndTime(medicineId: Long, date: String, time: String): DoseRecordEntity?

    @Query("SELECT * FROM dose_records WHERE medicineId = :medicineId AND scheduledDate = :date")
    suspend fun getDosesForMedicineAndDate(medicineId: Long, date: String): List<DoseRecordEntity>

    @Query("DELETE FROM dose_records WHERE medicineId = :medicineId AND scheduledDate = :date AND scheduledTime NOT IN (:activeTimes)")
    suspend fun deleteStaleDosesForMedicineAndDate(medicineId: Long, date: String, activeTimes: List<String>)

    @Query("DELETE FROM dose_records WHERE medicineId = :medicineId")
    suspend fun deleteDosesForMedicine(medicineId: Long)

    @Query("DELETE FROM dose_records")
    suspend fun clearAllDoses()

    @Query("SELECT COUNT(*) FROM dose_records WHERE scheduledDate = :date AND status = :status")
    fun getDoseCountByStatus(date: String, status: String): Flow<Int>
}
