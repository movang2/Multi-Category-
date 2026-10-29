package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_stations")
data class ShiftStation(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "🏭",
    val description: String = "",
    val qrCode: String = "",
    val isActive: Boolean = true
)

@Entity(tableName = "shift_tasks")
data class ShiftTask(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val stationId: String,
    val timeScheduled: String,
    val title: String,
    val instruction: String = "",
    val priority: String = "Bình thường", // "Cao", "Bình thường", "Thấp"
    val requiresConfirmation: Boolean = false,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val hasLoudAlarm: Boolean = false, // Phát âm thanh chuông báo thức to (kho cá, nấu ăn, việc khẩn cấp chống ngủ quên)
    val isHidden: Boolean = false, // Ẩn/Hiện: Nếu ẩn thì không tạo thông báo dù có bắt đầu ca hay không; nếu hiện thì thông báo bình thường khi đã bắt đầu ca
    val actionType: String = "", // "", "OPEN_APP", "LOCK_SCREEN"
    val targetPackageName: String = "", // Tên package của app cần mở (vd: com.zhiliaoapp.musically cho TikTok)
    val targetAppName: String = "" // Tên hiển thị của app (vd: TikTok, YouTube)
)

@Entity(tableName = "shift_handovers")
data class ShiftHandover(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val stationId: String,
    val stationName: String,
    val shiftTitle: String,
    val handoverContent: String,
    val senderName: String = "Người giao ca",
    val timestamp: Long = System.currentTimeMillis()
)
