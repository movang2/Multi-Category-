package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "roadmaps")
data class Roadmap(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String = "Nông nghiệp", // Nông nghiệp, Du lịch, Học tập, Sức khỏe, Đời sống, Khác
    val icon: String = "🌱",
    val startDate: String, // "yyyy-MM-dd"
    val totalDays: Int = 30,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "roadmap_tasks")
data class RoadmapTask(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val roadmapId: String,
    val dayOffset: Int = 0, // Số ngày tính từ ngày bắt đầu (0 = Ngày 1)
    val scheduledDate: String, // "yyyy-MM-dd"
    val scheduledTime: String = "08:00", // "HH:mm"
    val title: String,
    val instruction: String = "",
    val phase: String = "Giai đoạn 1", // Ví dụ: "Làm đất", "Chặng Miền Bắc", "Tháng thứ 1"...
    val priority: String = "Bình thường",
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val hasReminder: Boolean = true,
    val hasLoudAlarm: Boolean = false
)
