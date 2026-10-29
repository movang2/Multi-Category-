package com.example.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.ShiftDayAssignment
import com.example.data.ShiftRotationConfig
import com.example.data.ShiftStation
import com.example.receivers.ReminderReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ShiftRotationManager {

    private const val PREFS_NAME = "notes_app_prefs"
    private const val KEY_CONFIG_JSON = "shift_rotation_config_json"
    private const val ROTATION_ALARM_REQUEST_CODE = 8881
    private const val ROTATION_NOTIFICATION_CHANNEL_ID = "shift_rotation_channel"

    fun getMondayOfDate(timeMs: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timeMs
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getDayOfWeek1To7(cal: Calendar): Int {
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "Thứ Hai"
            2 -> "Thứ Ba"
            3 -> "Thứ Tư"
            4 -> "Thứ Năm"
            5 -> "Thứ Sáu"
            6 -> "Thứ Bảy"
            7 -> "Chủ Nhật"
            else -> "Thứ $dayOfWeek"
        }
    }

    fun getWeekIndex(anchorMondayMs: Long, targetMs: Long, totalWeeks: Int): Int {
        if (totalWeeks <= 1) return 0
        val targetMonday = getMondayOfDate(targetMs)
        val diffWeeks = ((targetMonday - anchorMondayMs) / (7L * 24 * 60 * 60 * 1000L)).toInt()
        val normalized = diffWeeks % totalWeeks
        return if (normalized < 0) normalized + totalWeeks else normalized
    }

    fun loadConfig(context: Context): ShiftRotationConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CONFIG_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            return createDefault2WeekConfig(getMondayOfDate(System.currentTimeMillis()))
        }

        return try {
            val root = JSONObject(jsonStr)
            val isEnabled = root.optBoolean("isEnabled", false)
            val cycleWeeks = root.optInt("cycleWeeks", 2).coerceAtLeast(1)
            val anchor = root.optLong("anchorMondayMillis", getMondayOfDate(System.currentTimeMillis()))
            val switchTime = root.optString("switchTimeStr", "06:00")
            val notify = root.optBoolean("notifyOnSwitch", true)

            val assignmentsList = mutableListOf<ShiftDayAssignment>()
            val arr = root.optJSONArray("assignments")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    assignmentsList.add(
                        ShiftDayAssignment(
                            weekIndex = obj.optInt("weekIndex", 0),
                            dayOfWeek = obj.optInt("dayOfWeek", 1),
                            stationId = obj.optString("stationId").takeIf { it.isNotBlank() },
                            targetStationName = obj.optString("targetStationName", "")
                        )
                    )
                }
            }

            ShiftRotationConfig(
                isEnabled = isEnabled,
                cycleWeeks = cycleWeeks,
                anchorMondayMillis = anchor,
                switchTimeStr = switchTime,
                notifyOnSwitch = notify,
                assignments = assignmentsList
            )
        } catch (e: Exception) {
            Log.e("ShiftRotationManager", "Failed to parse rotation config", e)
            createDefault2WeekConfig(getMondayOfDate(System.currentTimeMillis()))
        }
    }

    fun saveConfig(context: Context, config: ShiftRotationConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val root = JSONObject().apply {
                put("isEnabled", config.isEnabled)
                put("cycleWeeks", config.cycleWeeks)
                put("anchorMondayMillis", config.anchorMondayMillis)
                put("switchTimeStr", config.switchTimeStr)
                put("notifyOnSwitch", config.notifyOnSwitch)

                val arr = JSONArray()
                config.assignments.forEach { a ->
                    val obj = JSONObject().apply {
                        put("weekIndex", a.weekIndex)
                        put("dayOfWeek", a.dayOfWeek)
                        put("stationId", a.stationId ?: "")
                        put("targetStationName", a.targetStationName)
                    }
                    arr.put(obj)
                }
                put("assignments", arr)
            }
            prefs.edit().putString(KEY_CONFIG_JSON, root.toString()).apply()

            if (config.isEnabled) {
                scheduleNextRotationAlarm(context, config.switchTimeStr)
            } else {
                cancelRotationAlarm(context)
            }
        } catch (e: Exception) {
            Log.e("ShiftRotationManager", "Failed to save rotation config", e)
        }
    }

    fun createDefault2WeekConfig(anchorMonday: Long): ShiftRotationConfig {
        val assignments = mutableListOf<ShiftDayAssignment>()

        // Tuần 1 (weekIndex = 0): T2-T6 = "ca ngày", T7-CN = "ngày nghỉ"
        for (day in 1..5) {
            assignments.add(ShiftDayAssignment(0, day, null, "ca ngày"))
        }
        assignments.add(ShiftDayAssignment(0, 6, null, "ngày nghỉ"))
        assignments.add(ShiftDayAssignment(0, 7, null, "ngày nghỉ"))

        // Tuần 2 (weekIndex = 1): T2-T6 = "ca đêm", T7-CN = "ngày nghỉ"
        for (day in 1..5) {
            assignments.add(ShiftDayAssignment(1, day, null, "ca đêm"))
        }
        assignments.add(ShiftDayAssignment(1, 6, null, "ngày nghỉ"))
        assignments.add(ShiftDayAssignment(1, 7, null, "ngày nghỉ"))

        return ShiftRotationConfig(
            isEnabled = false,
            cycleWeeks = 2,
            anchorMondayMillis = anchorMonday,
            switchTimeStr = "06:00",
            notifyOnSwitch = true,
            assignments = assignments
        )
    }

    fun scheduleNextRotationAlarm(context: Context, switchTimeStr: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val parts = switchTimeStr.trim().replace("h", ":").split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 6
            val min = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val now = Calendar.getInstance()
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If time today has already passed, schedule for tomorrow
            if (targetCal.timeInMillis <= now.timeInMillis) {
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("IS_SHIFT_ROTATION_CHECK", true)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ROTATION_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetCal.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    targetCal.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("ShiftRotationManager", "Scheduled next rotation check at: ${targetCal.time}")
        } catch (e: Exception) {
            Log.e("ShiftRotationManager", "Failed to schedule rotation alarm", e)
        }
    }

    fun cancelRotationAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ROTATION_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun applyRotationForToday(context: Context): Pair<ShiftStation?, String> = withContext(Dispatchers.IO) {
        val config = loadConfig(context)
        if (!config.isEnabled) {
            return@withContext Pair(null, "Tự động xoay ca đang tắt")
        }

        val now = Calendar.getInstance()
        val currentWeek = getWeekIndex(config.anchorMondayMillis, now.timeInMillis, config.cycleWeeks)
        val dayOfWeek = getDayOfWeek1To7(now)
        val dayName = getDayName(dayOfWeek)

        val assignment = config.assignments.find { it.weekIndex == currentWeek && it.dayOfWeek == dayOfWeek }
        if (assignment == null) {
            return@withContext Pair(null, "Không có lịch phân bổ cho hôm nay")
        }

        val db = AppDatabase.getDatabase(context)
        val stations = db.shiftDao().getAllStationsDirect()

        // Match station by ID or by name
        val matchedStation = stations.find { it.id == assignment.stationId }
            ?: stations.find {
                it.name.trim().equals(assignment.targetStationName.trim(), ignoreCase = true)
            }
            ?: stations.find {
                val sName = it.name.lowercase(Locale.getDefault())
                val tName = assignment.targetStationName.lowercase(Locale.getDefault())
                sName.contains(tName) || tName.contains(sName)
            }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (matchedStation != null) {
            // Activate this station and stop others
            val oldStartedIds = prefs.getStringSet("started_station_ids", emptySet()) ?: emptySet()
            val allTasks = db.shiftDao().getAllTasksDirect()

            // Cancel alarms for previously started stations that are not the current matched station
            oldStartedIds.filter { it != matchedStation.id }.forEach { oldStationId ->
                val oldTasks = allTasks.filter { it.stationId == oldStationId }
                oldTasks.forEach { task ->
                    cancelTaskAlarmDirect(context, task)
                }
            }

            // Save new started station set
            val newStartedIds = setOf(matchedStation.id)
            prefs.edit()
                .putStringSet("started_station_ids", newStartedIds)
                .putBoolean("is_shift_started", true)
                .putString("selected_station_id", matchedStation.id)
                .putString("active_shift_time", "Chu kỳ xoay ca")
                .apply()

            // Schedule tasks for matched station
            val currentTasks = allTasks.filter { it.stationId == matchedStation.id && !it.isHidden && !it.isCompleted }
            currentTasks.forEach { task ->
                scheduleTaskAlarmDirect(context, task)
            }

            val msg = "Hôm nay là $dayName (Tuần ${currentWeek + 1}): Đã tự động kích hoạt ca '${matchedStation.name}'"

            if (config.notifyOnSwitch) {
                sendRotationNotification(
                    context,
                    title = "🔄 Chu kỳ ca: Bắt đầu ca hôm nay",
                    message = msg
                )
            }

            // Schedule check for tomorrow
            scheduleNextRotationAlarm(context, config.switchTimeStr)

            return@withContext Pair<ShiftStation?, String>(matchedStation, msg)
        } else {
            // No matching station found or it's a completely free day
            val msg = "Hôm nay là $dayName (Tuần ${currentWeek + 1}): ${assignment.targetStationName.ifBlank { "Không có ca" }}"
            scheduleNextRotationAlarm(context, config.switchTimeStr)
            return@withContext Pair<ShiftStation?, String>(null, msg)
        }
    }

    private fun scheduleTaskAlarmDirect(context: Context, task: com.example.data.ShiftTask) {
        val clean = task.timeScheduled.trim().replace("h", ":").replace(".", ":").replace("-", ":")
        val parts = clean.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("NOTE_ID", task.id)
                putExtra("NOTE_TEXT", task.title)
                putExtra("CATEGORY_NAME", "Ca làm việc")
                putExtra("IS_SHIFT_TASK", true)
                putExtra("HAS_LOUD_ALARM", task.hasLoudAlarm)
                putExtra("ACTION_TYPE", task.actionType)
                putExtra("TARGET_PACKAGE_NAME", task.targetPackageName)
                putExtra("TARGET_APP_NAME", task.targetAppName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelTaskAlarmDirect(context: Context, task: com.example.data.ShiftTask) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendRotationNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ROTATION_NOTIFICATION_CHANNEL_ID,
                "Thông báo Chu kỳ Ca làm",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo khi hệ thống tự động đổi ca theo chu kỳ tuần"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ROTATION_ALARM_REQUEST_CODE + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ROTATION_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(ROTATION_ALARM_REQUEST_CODE, notification)
    }
}
