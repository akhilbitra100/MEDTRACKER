package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.DoseRecordDao
import com.example.data.dao.MedicineDao
import com.example.data.model.DoseRecordEntity
import com.example.data.model.MedicineEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medicines ADD COLUMN mealInstruction TEXT NOT NULL DEFAULT 'No meal instruction specified'")
        db.execSQL("ALTER TABLE medicines ADD COLUMN mealInstructionRaw TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE medicines ADD COLUMN mealInstructionFlagged INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE dose_records ADD COLUMN mealInstruction TEXT NOT NULL DEFAULT 'No meal instruction specified'")
        db.execSQL("ALTER TABLE dose_records ADD COLUMN mealInstructionRaw TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medicines ADD COLUMN currentQuantity INTEGER NOT NULL DEFAULT 30")
        db.execSQL("ALTER TABLE medicines ADD COLUMN quantityUnit TEXT NOT NULL DEFAULT 'tablets'")
        db.execSQL("ALTER TABLE medicines ADD COLUMN doseQuantity INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE medicines ADD COLUMN lowStockThreshold INTEGER NOT NULL DEFAULT 5")
        db.execSQL("ALTER TABLE medicines ADD COLUMN refillAlertEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE dose_records ADD COLUMN doseDeducted INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medicines ADD COLUMN reminderTimes TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dose_records ADD COLUMN snoozedUntil INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [MedicineEntity::class, DoseRecordEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun doseRecordDao(): DoseRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dosebuddy_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
