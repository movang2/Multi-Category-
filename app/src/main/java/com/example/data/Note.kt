package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val categoryName: String,
    val text: String,
    val images: List<String> = emptyList(), // Local file absolute paths
    val tags: List<String> = emptyList(),   // Custom tags (e.g., "#học_tiếng_hàn")
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val reminderTime: Long? = null,         // Local alarm timestamp in milliseconds
    val isLocked: Boolean = false,          // Secure PIN protection or biometrics
    val passwordHash: String? = null,       // Specific password/hash if locked
    val voicePath: String? = null,          // Voice recording absolute path
    val audioDurationMs: Long = 0L          // Duration of voice recording
)

class Converters {
    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(";;;")
    }

    @TypeConverter
    fun fromList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return ""
        return list.joinToString(";;;")
    }
}
