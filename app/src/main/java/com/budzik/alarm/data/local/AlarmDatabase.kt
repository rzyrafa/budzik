package com.budzik.alarm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, RingingStateEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun ringingStateDao(): RingingStateDao

    companion object {
        /** v4 adds [AlarmEntity.type]; existing rows are classified the same way the old label checks did. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN type TEXT NOT NULL DEFAULT 'SINGLE'")
                db.execSQL("UPDATE alarms SET type = 'SERIES' WHERE seriesId IS NOT NULL AND seriesId != ''")
                db.execSQL("UPDATE alarms SET type = 'NAP' WHERE label LIKE 'Drzemka%'")
                db.execSQL("UPDATE alarms SET type = 'TEST' WHERE label LIKE 'Test%'")
            }
        }

        @Volatile
        private var INSTANCE: AlarmDatabase? = null

        fun getDatabase(context: Context): AlarmDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "budzik_alarm_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    // Only pre-release schemas may be wiped; later versions need a real Migration
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
