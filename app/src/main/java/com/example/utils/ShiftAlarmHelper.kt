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
            // Nếu mốc giờ đã qua hoặc đang nằm trong khoảng 10 giây hiện tại, bắt buộc lên lịch cho ngày hôm sau
            if (forceTomorrow || calendar.timeInMillis <= now + 10_000L) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            calendar.timeInMillis
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val noteDetail = if (task.instruction.isNotBlank()) "📝 Chi tiết: ${task.instruction}" else "📋 Công việc: ${task.title}"

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

    private fun parseHourMinute(timeStr: String): Pair<Int, Int>? {
        val parts = timeStr.trim().split(":")
        if (parts.size >= 2) {
            val h = parts[0].trim().toIntOrNull()
            val m = parts[1].trim().take(2).toIntOrNull()
            if (h != null && m != null && h in 0..23 && m in 0..59) {
                return Pair(h, m)
            }
        }
        return null
    }
}
