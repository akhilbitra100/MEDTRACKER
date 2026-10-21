package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.MedicineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines ORDER BY createdAt DESC")
    fun getAllMedicines(): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE id = :id LIMIT 1")
    suspend fun getMedicineById(id: Long): MedicineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: MedicineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicines(medicines: List<MedicineEntity>): List<Long>

    @Update
    suspend fun updateMedicine(medicine: MedicineEntity)

    @Delete
    suspend fun deleteMedicine(medicine: MedicineEntity)

    @Query("DELETE FROM medicines WHERE id = :id")
    suspend fun deleteMedicineById(id: Long)

    @Query("SELECT COUNT(*) FROM medicines")
    fun getMedicineCount(): Flow<Int>

    @Query("UPDATE medicines SET currentQuantity = :newQuantity WHERE id = :id")
    suspend fun updateMedicineQuantity(id: Long, newQuantity: Int)

    @Query("UPDATE medicines SET reminderTimes = :reminderTimes WHERE id = :id")
    suspend fun updateReminderTimes(id: Long, reminderTimes: String)

    @Query("UPDATE medicines SET currentQuantity = :quantity, quantityUnit = :unit, doseQuantity = :doseQty, lowStockThreshold = :threshold, refillAlertEnabled = :alertEnabled WHERE id = :id")
    suspend fun updateRefillSettings(
        id: Long,
        quantity: Int,
        unit: String,
        doseQty: Int,
        threshold: Int,
        alertEnabled: Boolean
    )

    @Query("DELETE FROM medicines")
    suspend fun clearAllMedicines()

}
