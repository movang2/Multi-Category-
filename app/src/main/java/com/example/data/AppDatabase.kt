package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Category::class,
        Note::class,
        QuickNote::class,
        ShiftStation::class,
        ShiftTask::class,
        ShiftHandover::class,
        Roadmap::class,
        RoadmapTask::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun quickNoteDao(): QuickNoteDao
    abstract fun shiftDao(): ShiftDao
    abstract fun roadmapDao(): RoadmapDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE shift_tasks ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE shift_tasks ADD COLUMN actionType TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE shift_tasks ADD COLUMN targetPackageName TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE shift_tasks ADD COLUMN targetAppName TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS roadmaps (
                            id TEXT NOT NULL PRIMARY KEY,
                            title TEXT NOT NULL,
                            category TEXT NOT NULL,
                            icon TEXT NOT NULL,
                            startDate TEXT NOT NULL,
                            totalDays INTEGER NOT NULL,
                            description TEXT NOT NULL,
                            createdAt INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS roadmap_tasks (
                            id TEXT NOT NULL PRIMARY KEY,
                            roadmapId TEXT NOT NULL,
                            dayOffset INTEGER NOT NULL,
                            scheduledDate TEXT NOT NULL,
                            scheduledTime TEXT NOT NULL,
                            title TEXT NOT NULL,
                            instruction TEXT NOT NULL,
                            phase TEXT NOT NULL,
                            priority TEXT NOT NULL,
                            isCompleted INTEGER NOT NULL,
                            completedAt INTEGER,
                            hasReminder INTEGER NOT NULL,
                            hasLoudAlarm INTEGER NOT NULL
                        )
                    """.trimIndent())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE quick_notes ADD COLUMN hasNotification INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE quick_notes ADD COLUMN reminderTime INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE quick_notes ADD COLUMN isNotificationTriggered INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "multi_category_notes_db"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
