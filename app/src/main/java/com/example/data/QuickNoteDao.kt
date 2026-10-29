package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickNoteDao {
    @Query("SELECT * FROM quick_notes ORDER BY pinned DESC, createdAt DESC")
    fun getAllQuickNotesFlow(): Flow<List<QuickNote>>

    @Query("SELECT * FROM quick_notes ORDER BY pinned DESC, createdAt DESC")
    suspend fun getAllQuickNotesDirect(): List<QuickNote>

    @Query("SELECT * FROM quick_notes WHERE id = :id LIMIT 1")
    suspend fun getQuickNoteById(id: String): QuickNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickNote(note: QuickNote)

    @Delete
    suspend fun deleteQuickNote(note: QuickNote)

    @Query("DELETE FROM quick_notes")
    suspend fun deleteAllQuickNotes()

    @Query("SELECT * FROM quick_notes WHERE hasNotification = 1 AND reminderTime IS NOT NULL AND reminderTime > :currentTime AND isNotificationTriggered = 0")
    suspend fun getFutureQuickNoteReminders(currentTime: Long): List<QuickNote>
}
