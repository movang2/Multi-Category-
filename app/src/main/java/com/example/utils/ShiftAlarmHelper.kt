package com.example.utils

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.data.ShiftTask
import com.example.receivers.ReminderReceiver

object ShiftAlarmHelper {

    fun scheduleShiftTaskAlarm(
        context: Context,
        task: ShiftTask,
        stationName: String = "",
        explicitTriggerTimeMs: Long? = null,
        forceTomorrow: Boolean = false
    ) {
        if (task.isHidden || task.isCompleted) return

        val parsed = parseHourMinute(task.timeScheduled) ?: return
        val hour = parsed.first
        val minute = parsed.second

        var triggerTimeMs = explicitTriggerTimeMs ?: run {
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val now = System.currentTimeMillis()
            val diff = calendar.timeInMillis - now

            if (forceTomorrow) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                calendar.timeInMillis
            } else if (diff >= 2_000L) {
                // Mốc giờ còn trong tương lai hôm nay (cách ít nhất 2 giây)
                calendar.timeInMillis
            } else if (diff >= -59_000L) {
                // Mốc giờ chính là phút hiện tại (ví dụ vừa tạo hoặc vừa bắt đầu ca lúc 19:35 cho việc 19:35)
                // Kích hoạt chuông/thông báo ngay lập tức sau 1.5 giây để không bị trôi sang ngày hôm sau!
                now + 1_500L
            } else {
                // Mốc giờ đã trôi qua hơn 1 phút trước -> Lên lịch cho ngày mai
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                calendar.timeInMillis
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val noteDetail = if (task.instruction.isNotBlank()) "${task.title} : ${task.instruction}" else task.title

        // Xóa cờ hủy cũ nếu có để sẵn sàng cho lần chạy mới
        val prefs = context.getSharedPreferences("notes_app_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("cancelled_auto_${task.id}").apply()

        val isAutomation = task.actionType == "OPEN_APP" || task.actionType == "LOCK_SCREEN"
        val now = System.currentTimeMillis()

        // 1. Lên lịch thông báo báo trước 1 phút cho tính năng tự động (Mở app hoặc Khóa máy đi ngủ)
        if (isAutomation) {
            val preWarningMs = triggerTimeMs - 60_000L
            val preWarningIntent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("NOTE_ID", task.id)
                putExtra("TASK_TITLE", task.title)
                putExtra("TASK_INSTRUCTION", task.instruction)
                putExtra("NOTE_TEXT", noteDetail)
                putExtra("CATEGORY_NAME", if (task.actionType == "LOCK_SCREEN") "Chế độ Đi ngủ (${task.timeScheduled})" else "Hẹn giờ mở app (${task.timeScheduled})")
                putExtra("IS_SHIFT_TASK", true)
                putExtra("STATION_ID", task.stationId)
                putExtra("STATION_NAME", stationName)
                putExtra("TIME_SCHEDULED", task.timeScheduled)
                putExtra("HAS_LOUD_ALARM", false) // Thông báo trước không kêu chuông inh ỏi
                putExtra("ACTION_TYPE", task.actionType)
                putExtra("TARGET_PACKAGE_NAME", task.targetPackageName)
                putExtra("TARGET_APP_NAME", task.targetAppName)
                putExtra(ReminderReceiver.IS_AUTOMATION_PRE_WARNING, true)
            }
            val prePendingIntent = PendingIntent.getBroadcast(
                context,
                (task.id + "_pre_warning").hashCode(),
                preWarningIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val effPreMs = if (preWarningMs > now + 3_000L) {
                preWarningMs
            } else if (triggerTimeMs > now + 10_000L) {
                now + 1_000L // Nếu thời gian hẹn chỉ còn dưới 1 phút, báo trước ngay sau 1s
            } else null

            if (effPreMs != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, effPreMs, prePendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, effPreMs, prePendingIntent)
                    }
                } catch (e: Exception) {
                    try {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, effPreMs, prePendingIntent)
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            }
        }

        // 2. Lên lịch báo thức chính thức tại mốc thời gian đã định
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("NOTE_ID", task.id)
            putExtra("TASK_TITLE", task.title)
            putExtra("TASK_INSTRUCTION", task.instruction)
            putExtra("NOTE_TEXT", noteDetail)
            putExtra("CATEGORY_NAME", if (task.hasLoudAlarm) "Chuông báo việc (${task.timeScheduled})" else "Nhắc ca làm (${task.timeScheduled})")
            putExtra("IS_SHIFT_TASK", true)
            putExtra("STATION_ID", task.stationId)
            putExtra("STATION_NAME", stationName)
            putExtra("TIME_SCHEDULED", task.timeScheduled)
            putExtra("HAS_LOUD_ALARM", task.hasLoudAlarm)
            putExtra("ACTION_TYPE", task.actionType)
            putExtra("TARGET_PACKAGE_NAME", task.targetPackageName)
            putExtra("TARGET_APP_NAME", task.targetAppName)
            putExtra(ReminderReceiver.IS_AUTOMATION_PRE_WARNING, false)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
        } catch (e: Exception) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    fun cancelShiftTaskAlarm(context: Context, task: ShiftTask) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        
        // Hủy alarm thực hiện chính
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(pendingIntent)

        // Hủy alarm thông báo trước 1 phút
        val prePendingIntent = PendingIntent.getBroadcast(
            context,
            (task.id + "_pre_warning").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(prePendingIntent)

        // Xóa cờ hủy
        val prefs = context.getSharedPreferences("notes_app_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("cancelled_auto_${task.id}").apply()

        // Hủy thông báo hiển thị trên thanh trạng thái (nếu đang hiện)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(task.id.hashCode())
    }

    fun parseHourMinute(timeStr: String): Pair<Int, Int>? {
        if (timeStr.isBlank()) return null
        val clean = timeStr.trim().lowercase(java.util.Locale.getDefault())
            .replace("h", ":")
            .replace("H", ":")
            .replace("g", ":")
            .replace("giờ", ":")
            .replace(".", ":")
            .replace("-", ":")
        val parts = clean.split(":")
        if (parts.size >= 2) {
            val h = parts[0].trim().toIntOrNull()
            val m = if (parts[1].isBlank()) 0 else parts[1].trim().take(2).toIntOrNull()
            if (h != null && m != null && h in 0..23 && m in 0..59) {
                return Pair(h, m)
            }
        } else if (parts.size == 1) {
            val h = parts[0].trim().toIntOrNull()
            if (h != null && h in 0..23) {
                return Pair(h, 0)
            }
        }
        return null
    }

    /**
     * Tách và chuẩn hóa tất cả các mốc thời gian xuất hiện trong chuỗi nhập vào.
     * Hỗ trợ định dạng dùng dấu ':' (7:30, 22:15) hoặc chữ 'h'/'H'/'g'/'giờ' (7h30, 22h15, 7h).
     * Hỗ trợ danh sách nhiều mốc giờ phân cách bởi dấu phẩy, chấm phẩy, khoảng trắng.
     * Ví dụ: "7:30, 9:20, 10:11, 12:18" -> ["07:30", "09:20", "10:11", "12:18"]
     *         "7h30, 9h20, 10h11, 12h18" -> ["07:30", "09:20", "10:11", "12:18"]
     */
    fun parseMultipleTimes(input: String): List<String> {
        if (input.isBlank()) return emptyList()

        // Biểu thức regex tìm mọi mốc giờ: 7:30, 07:30, 7h30, 7h, 7H30, 7.30, 7g30, 7 giờ 30
        val timeRegex = Regex("""(?i)\b([0-1]?[0-9]|2[0-3])\s*(?:[:\.]\s*([0-5]?[0-9])|(?:[hHgG]|giờ)\s*([0-5]?[0-9])?)\b""")
        val results = mutableListOf<String>()

        val matches = timeRegex.findAll(input).toList()
        if (matches.isNotEmpty()) {
            for (m in matches) {
                val h = m.groupValues[1].toIntOrNull() ?: continue
                val minStr = when {
                    m.groupValues[2].isNotBlank() -> m.groupValues[2]
                    m.groupValues[3].isNotBlank() -> m.groupValues[3]
                    else -> "0"
                }
                val min = minStr.toIntOrNull() ?: 0
                if (h in 0..23 && min in 0..59) {
                    val formatted = String.format(java.util.Locale.getDefault(), "%02d:%02d", h, min)
                    if (!results.contains(formatted)) {
                        results.add(formatted)
                    }
                }
            }
        }

        // Dự phòng nếu không khớp regex (ví dụ chuỗi đơn giản):
        if (results.isEmpty()) {
            val segments = input.split(Regex("[,;|\n\r\\s]+")).map { it.trim() }.filter { it.isNotBlank() }
            for (seg in segments) {
                val parsed = parseHourMinute(seg)
                if (parsed != null) {
                    val formatted = String.format(java.util.Locale.getDefault(), "%02d:%02d", parsed.first, parsed.second)
                    if (!results.contains(formatted)) {
                        results.add(formatted)
                    }
                }
            }
        }

        return results
    }
}
