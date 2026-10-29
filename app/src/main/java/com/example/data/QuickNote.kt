package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_notes")
data class QuickNote(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val hasNotification: Boolean = false,
    val reminderTime: Long? = null,
    val isNotificationTriggered: Boolean = false
)
