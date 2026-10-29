package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // --- CATEGORIES ---
    @Query("SELECT * FROM categories ORDER BY createdAt DESC")
    fun getCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY createdAt DESC")
    suspend fun getCategoriesDirect(): List<Category>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Transaction
    suspend fun renameCategoryTransaction(oldName: String, newName: String) {
        insertCategory(Category(name = newName))
        updateNotesCategory(oldName, newName)
        deleteCategory(Category(name = oldName))
    }

    // --- NOTES ---
    @Query("SELECT * FROM notes WHERE categoryName = :categoryName ORDER BY pinned DESC, createdAt DESC")
    fun getNotesByCategory(categoryName: String): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY pinned DESC, createdAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY pinned DESC, createdAt DESC")
    suspend fun getAllNotesDirect(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): Note?

    @Query("SELECT * FROM notes WHERE reminderTime IS NOT NULL AND reminderTime > :currentTime")
    suspend fun getFutureReminders(currentTime: Long): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("UPDATE notes SET categoryName = :newName WHERE categoryName = :oldName")
    suspend fun updateNotesCategory(oldName: String, newName: String)

    @Query("DELETE FROM notes WHERE categoryName = :categoryName")
    suspend fun deleteNotesByCategory(categoryName: String)
}
