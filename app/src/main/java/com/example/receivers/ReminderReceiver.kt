package com.example.receivers

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == ACTION_DISMISS_ALARM) {
                stopAlarm()
                val notifId = intent.getIntExtra("NOTIFICATION_ID", 0)
                if (notifId != 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(notifId)
                }
                return
            }

            if (intent.action == ACTION_EXECUTE_AUTOMATION_NOW) {
                val notifId = intent.getIntExtra("NOTIFICATION_ID", 0)
                if (notifId != 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(notifId)
                }
                val noteId = intent.getStringExtra("NOTE_ID") ?: ""
                val actType = intent.getStringExtra("ACTION_TYPE") ?: ""
                val targetPkg = intent.getStringExtra("TARGET_PACKAGE_NAME") ?: ""
                val targetName = intent.getStringExtra("TARGET_APP_NAME") ?: ""

                if (noteId.isNotBlank()) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    val cancelPendingIntent = PendingIntent.getBroadcast(
                        context,
                        noteId.hashCode(),
                        Intent(context, ReminderReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager?.cancel(cancelPendingIntent)

                    val prefs = context.getSharedPreferences("notes_app_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("cancelled_auto_$noteId", true).apply()
                }

                if (actType == "LOCK_SCREEN") {
                    try {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(homeIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (!isEmulator()) {
                        try {
                            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                            val comp = android.content.ComponentName(context, AppDeviceAdminReceiver::class.java)
                            if (dpm != null && dpm.isAdminActive(comp)) {
                                dpm.lockNow()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                "🌙 Chế độ Đi ngủ: Đã đóng các app về Home Screen. (Trên điện thoại thật sẽ tự khóa tắt màn hình)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else if (actType == "OPEN_APP" && targetPkg.isNotBlank()) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)?.apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        }
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(context, "🚀 Đang mở ${targetName.ifBlank { "ứng dụng" }} ngay...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return
            }

            if (intent.action == ACTION_CANCEL_AUTOMATION) {
                val notifId = intent.getIntExtra("NOTIFICATION_ID", 0)
                if (notifId != 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(notifId)
                }
                val noteId = intent.getStringExtra("NOTE_ID") ?: ""
                val timeScheduled = intent.getStringExtra("TIME_SCHEDULED") ?: ""
                val actType = intent.getStringExtra("ACTION_TYPE") ?: ""

                if (noteId.isNotBlank()) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    val cancelPendingIntent = PendingIntent.getBroadcast(
                        context,
                        noteId.hashCode(),
                        Intent(context, ReminderReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager?.cancel(cancelPendingIntent)

                    val prefs = context.getSharedPreferences("notes_app_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("cancelled_auto_$noteId", true).apply()
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val actionDesc = if (actType == "LOCK_SCREEN") "khóa máy đi ngủ" else "mở ứng dụng"
                    val msg = if (timeScheduled.isNotBlank()) "❌ Đã hủy $actionDesc lúc $timeScheduled" else "❌ Đã hủy thực hiện tự động lần này"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                return
            }

            if (intent.action == ACTION_LOCK_DEVICE) {
                stopAlarm()
                val notifId = intent.getIntExtra("NOTIFICATION_ID", 0)
                if (notifId != 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(notifId)
                }
                try {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (!isEmulator()) {
                    try {
                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                        val comp = android.content.ComponentName(context, AppDeviceAdminReceiver::class.java)
                        if (dpm != null && dpm.isAdminActive(comp)) {
                            dpm.lockNow()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            context,
                            "🌙 Chế độ Đi ngủ: Đã thu nhỏ app về Home Screen. (Trên điện thoại thật sẽ tự khóa tắt màn hình)",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                return
            }

            val isShiftRotationCheck = intent.getBooleanExtra("IS_SHIFT_ROTATION_CHECK", false)
            if (isShiftRotationCheck) {
                CoroutineScope(Dispatchers.IO).launch {
                    com.example.utils.ShiftRotationManager.applyRotationForToday(context)
                }
                return
            }

            val isShiftTask = intent.getBooleanExtra("IS_SHIFT_TASK", false)
            val hasLoudAlarm = intent.getBooleanExtra("HAS_LOUD_ALARM", false)
            val actionType = intent.getStringExtra("ACTION_TYPE") ?: ""
            val targetPackageName = intent.getStringExtra("TARGET_PACKAGE_NAME") ?: ""
            val targetAppName = intent.getStringExtra("TARGET_APP_NAME") ?: ""

            val isRoadmapTask = intent.getBooleanExtra("IS_ROADMAP_TASK", false)
            val isQuickNote = intent.getBooleanExtra("IS_QUICK_NOTE", false)

            if (isQuickNote) {
                val qnId = intent.getStringExtra("NOTE_ID")
                if (qnId != null) {
                    try {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            val db = com.example.data.AppDatabase.getDatabase(context)
                            val qn = db.quickNoteDao().getQuickNoteById(qnId)
                            if (qn != null) {
                                db.quickNoteDao().insertQuickNote(qn.copy(isNotificationTriggered = true))
                                com.example.utils.QuickNoteAlarmHelper.updateWidget(context)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (isRoadmapTask) {
                val taskId = intent.getStringExtra("NOTE_ID")
                if (taskId != null) {
                    try {
                        val isDone = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                            val db = com.example.data.AppDatabase.getDatabase(context)
                            val task = db.roadmapDao().getTaskById(taskId)
                            task == null || task.isCompleted
                        }
                        if (isDone) {
                            return
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            var activeDbTask: com.example.data.ShiftTask? = null
            var activeDbStation: com.example.data.ShiftStation? = null

            val prefs = context.getSharedPreferences("notes_app_prefs", Context.MODE_PRIVATE)

            if (isShiftTask) {
                val startedStationIds = prefs.getStringSet("started_station_ids", null) ?: emptySet()
                val stationId = intent.getStringExtra("STATION_ID")
                val isShiftStarted = prefs.getBoolean("is_shift_started", false)

                val isStationActive = if (!stationId.isNullOrBlank()) {
                    startedStationIds.contains(stationId)
                } else {
                    startedStationIds.isNotEmpty() || isShiftStarted
                }

                if (!isStationActive) {
                    // Ca làm của vị trí này chưa được bắt đầu hoặc đã kết thúc, không gửi thông báo
                    return
                }

                // Luôn truy vấn trực tiếp cơ sở dữ liệu SQLite để lấy bản ghi công việc mới nhất 100% (cả tiêu đề và hướng dẫn chi tiết)
                val taskId = intent.getStringExtra("NOTE_ID")
                if (taskId != null) {
                    try {
                        val taskAndStation = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                            val db = com.example.data.AppDatabase.getDatabase(context)
                            val t = db.shiftDao().getTaskById(taskId)
                                ?: db.shiftDao().getAllTasksDirect().find { it.id == taskId }
                            val st = if (t != null) db.shiftDao().getStationById(t.stationId) else null
                            Pair(t, st)
                        }
                        activeDbTask = taskAndStation.first
                        activeDbStation = taskAndStation.second

                        if (activeDbTask != null && (activeDbTask!!.isHidden || activeDbTask!!.isCompleted)) {
                            // Công việc bị ẨN hoặc Đã hoàn thành -> Tuyệt đối không phát thông báo
                            return
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val effActionType = if (isShiftTask && activeDbTask != null) activeDbTask!!.actionType.ifBlank { actionType } else actionType
            val effTargetPackage = if (isShiftTask && activeDbTask != null) activeDbTask!!.targetPackageName.ifBlank { targetPackageName } else targetPackageName
            val effTargetAppName = if (isShiftTask && activeDbTask != null) activeDbTask!!.targetAppName.ifBlank { targetAppName } else targetAppName
            val effHasLoudAlarm = if (isShiftTask && activeDbTask != null) activeDbTask!!.hasLoudAlarm else hasLoudAlarm

            val isAutomationPreWarning = intent.getBooleanExtra(IS_AUTOMATION_PRE_WARNING, false)
            val noteId = intent.getStringExtra("NOTE_ID") ?: System.currentTimeMillis().toString()

            // KHÓA CHỐNG BẮN ĐÚP (Anti-duplicate de-bounce guard):
            // Ngăn chặn triệt để trường hợp Android AlarmManager kích hoạt 2 broadcast cách nhau vài giây
            val guardKey = "last_notify_ts_${noteId}_$isAutomationPreWarning"
            val lastNotifyTs = prefs.getLong(guardKey, 0L)
            val currentTs = System.currentTimeMillis()
            if (currentTs - lastNotifyTs < 25_000L) {
                // Đã vừa phát thông báo cho tác vụ này cách đây dưới 25 giây -> Bỏ qua ngay lập tức!
                return
            }
            prefs.edit().putLong(guardKey, currentTs).apply()

            val rawNoteText = intent.getStringExtra("NOTE_TEXT") ?: "Có công việc cần thực hiện!"
            val categoryName = intent.getStringExtra("CATEGORY_NAME") ?: "Nhắc nhở công việc & Ca làm"

            val effTaskTitle = activeDbTask?.title
                ?: intent.getStringExtra("TASK_TITLE")
                ?: rawNoteText.substringBefore(":").removePrefix("📋 ").trim().ifBlank { "Công việc theo ca" }

            val effTaskInstruction = activeDbTask?.instruction
                ?: intent.getStringExtra("TASK_INSTRUCTION")
                ?: if (rawNoteText.contains(":")) rawNoteText.substringAfter(":").trim() else ""

            val effTimeScheduled = activeDbTask?.timeScheduled
                ?: intent.getStringExtra("TIME_SCHEDULED")
                ?: ""

            val stationName = activeDbStation?.name ?: intent.getStringExtra("STATION_NAME") ?: ""

            // 1. NẾU LÀ CẢNH BÁO TRƯỚC 1 PHÚT CHO TÍNH NĂNG MỞ APP HOẶC KHÓA MÀN HÌNH ĐI NGỦ
            if (isAutomationPreWarning && (effActionType == "OPEN_APP" || effActionType == "LOCK_SCREEN")) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val channelId = "notes_automation_prewarning"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
                    val channel = NotificationChannel(
                        channelId,
                        "Cảnh báo tự động hóa (Trước 1 phút)",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Thông báo cảnh báo trước 1 phút khi sắp tự động mở app hoặc tắt máy đi ngủ, cho phép thực hiện ngay hoặc hủy bỏ"
                        enableLights(true)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 400, 200, 400)
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val preTitle = if (effActionType == "OPEN_APP") {
                    "⏳ [SẮP ĐẾN GIỜ] Mở ${effTargetAppName.ifBlank { "ứng dụng" }} (sau 1 phút)"
                } else {
                    "🌙 [SẮP ĐẾN GIỜ] Khóa màn hình & Đi ngủ (sau 1 phút)"
                }

                val preSummary = if (stationName.isNotBlank()) "📍 $stationName" else "Sắp thực hiện"

                val preBody = if (effActionType == "OPEN_APP") {
                    buildString {
                        append("Ứng dụng ${effTargetAppName.ifBlank { "đã chọn" }} sẽ tự động mở lúc $effTimeScheduled để làm: $effTaskTitle.")
                        if (effTaskInstruction.isNotBlank()) {
                            append("\n\n📖 Chi tiết: $effTaskInstruction")
                        }
                        append("\n\n👉 Bạn có thể nhấn [⚡ Mở ngay] nếu muốn bắt đầu sớm hoặc [❌ Hủy lần này] nếu muốn bỏ qua.")
                    }
                } else {
                    buildString {
                        append("Hệ thống sẽ tự động đóng ứng dụng và khóa màn hình lúc $effTimeScheduled ($effTaskTitle).")
                        if (effTaskInstruction.isNotBlank()) {
                            append("\n\n📖 Chi tiết: $effTaskInstruction")
                        }
                        append("\n\n👉 Bạn có thể nhấn [🔒 Khóa ngay] để nghỉ ngơi sớm hoặc [❌ Hủy lần này] nếu chưa muốn tắt máy.")
                    }
                }

                // Nút 1: Thực hiện ngay
                val execIntent = Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_EXECUTE_AUTOMATION_NOW
                    putExtra("NOTE_ID", noteId)
                    putExtra("ACTION_TYPE", effActionType)
                    putExtra("TARGET_PACKAGE_NAME", effTargetPackage)
                    putExtra("TARGET_APP_NAME", effTargetAppName)
                    putExtra("NOTIFICATION_ID", noteId.hashCode())
                }
                val execPendingIntent = PendingIntent.getBroadcast(
                    context,
                    (noteId + "_exec_now").hashCode(),
                    execIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val execLabel = if (effActionType == "OPEN_APP") "⚡ Mở ngay" else "🔒 Khóa ngay"

                // Nút 2: Hủy bỏ lần này
                val cancelIntent = Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_CANCEL_AUTOMATION
                    putExtra("NOTE_ID", noteId)
                    putExtra("ACTION_TYPE", effActionType)
                    putExtra("TIME_SCHEDULED", effTimeScheduled)
                    putExtra("NOTIFICATION_ID", noteId.hashCode())
                }
                val cancelPendingIntent = PendingIntent.getBroadcast(
                    context,
                    (noteId + "_cancel_auto").hashCode(),
                    cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val cancelLabel = "❌ Hủy lần này"

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val contentPendingIntent = PendingIntent.getActivity(
                    context,
                    noteId.hashCode(),
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val preNotification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                    .setContentTitle(preTitle)
                    .setContentText(if (effActionType == "OPEN_APP") "Sắp tự động mở $effTargetAppName sau 1 phút" else "Sắp tự động khóa màn hình sau 1 phút")
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .setBigContentTitle(preTitle)
                            .setSummaryText(preSummary)
                            .bigText(preBody)
                    )
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(contentPendingIntent)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setTimeoutAfter(70_000L)
                    .addAction(
                        if (effActionType == "OPEN_APP") android.R.drawable.ic_menu_send else android.R.drawable.ic_lock_power_off,
                        execLabel,
                        execPendingIntent
                    )
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        cancelLabel,
                        cancelPendingIntent
                    )
                    .build()

                notificationManager?.notify(noteId.hashCode(), preNotification)
                return
            }

            // 2. KIỂM TRA NẾU NGƯỜI DÙNG ĐÃ BẤM HỦY HOẶC ĐÃ THỰC HIỆN SỚM TRƯỚC ĐÓ
            val isCancelled = prefs.getBoolean("cancelled_auto_$noteId", false)
            if (isCancelled) {
                prefs.edit().remove("cancelled_auto_$noteId").apply()
                // Tự động lên lịch lại cho chu kỳ ngày mai nếu là ca làm lặp lại
                if (isShiftTask) {
                    val taskToReschedule = activeDbTask ?: com.example.data.ShiftTask(
                        id = noteId,
                        stationId = activeDbStation?.id ?: intent.getStringExtra("STATION_ID") ?: "",
                        timeScheduled = effTimeScheduled,
                        title = effTaskTitle,
                        instruction = effTaskInstruction,
                        hasLoudAlarm = effHasLoudAlarm,
                        actionType = effActionType,
                        targetPackageName = effTargetPackage,
                        targetAppName = effTargetAppName
                    )
                    com.example.utils.ShiftAlarmHelper.scheduleShiftTaskAlarm(
                        context = context,
                        task = taskToReschedule,
                        stationName = stationName,
                        forceTomorrow = true
                    )
                }
                return
            }

            // XỬ LÝ HÀNH ĐỘNG TỰ ĐỘNG HÓA
            // 1. Tự động đóng app về Home & Khóa màn hình (ví dụ: tắt app đi ngủ lúc 22:15)
            if (effActionType == "LOCK_SCREEN") {
                try {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (!isEmulator()) {
                    try {
                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                        val comp = android.content.ComponentName(context, AppDeviceAdminReceiver::class.java)
                        if (dpm != null && dpm.isAdminActive(comp)) {
                            dpm.lockNow()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            context,
                            "🌙 Chế độ Đi ngủ: Đã đóng các app về Home Screen. (Trên điện thoại thật sẽ tự khóa tắt màn hình)",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Tự động xóa sạch thông báo cảnh báo trước 1 phút để không để lại rác trên thanh thông báo/đồng hồ
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(noteId.hashCode())

                // Tự động lên lịch lại cho ca làm việc vào ngày mai
                if (isShiftTask && effTimeScheduled.isNotBlank()) {
                    val taskToReschedule = activeDbTask ?: com.example.data.ShiftTask(
                        id = noteId,
                        stationId = activeDbStation?.id ?: intent.getStringExtra("STATION_ID") ?: "",
                        timeScheduled = effTimeScheduled,
                        title = effTaskTitle,
                        instruction = effTaskInstruction,
                        hasLoudAlarm = effHasLoudAlarm,
                        actionType = effActionType,
                        targetPackageName = effTargetPackage,
                        targetAppName = effTargetAppName
                    )
                    com.example.utils.ShiftAlarmHelper.scheduleShiftTaskAlarm(
                        context = context,
                        task = taskToReschedule,
                        stationName = stationName,
                        forceTomorrow = true
                    )
                }
                return
            }

            // 2. Tự động mở ứng dụng đã chọn trong máy (ví dụ: mở TikTok lúc 06:00)
            if (effActionType == "OPEN_APP" && effTargetPackage.isNotBlank()) {
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(effTargetPackage)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    if (launchIntent != null) {
                        // Khởi chạy trực tiếp từ background nếu được phép (Overlay/Foreground/Unlocked)
                        context.startActivity(launchIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Tự động xóa sạch thông báo cảnh báo trước 1 phút để không để lại rác trên thanh thông báo/đồng hồ
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(noteId.hashCode())

                // Tự động lên lịch lại cho ca làm việc vào ngày mai
                if (isShiftTask && effTimeScheduled.isNotBlank()) {
                    val taskToReschedule = activeDbTask ?: com.example.data.ShiftTask(
                        id = noteId,
                        stationId = activeDbStation?.id ?: intent.getStringExtra("STATION_ID") ?: "",
                        timeScheduled = effTimeScheduled,
                        title = effTaskTitle,
                        instruction = effTaskInstruction,
                        hasLoudAlarm = effHasLoudAlarm,
                        actionType = effActionType,
                        targetPackageName = effTargetPackage,
                        targetAppName = effTargetAppName
                    )
                    com.example.utils.ShiftAlarmHelper.scheduleShiftTaskAlarm(
                        context = context,
                        task = taskToReschedule,
                        stationName = stationName,
                        forceTomorrow = true
                    )
                }
                return
            }

            // 3. Tự động chuyển Chế độ Âm thanh (Im lặng / Rung / Chuông bình thường)
            if (effActionType == "SILENT_MODE") {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.ringerMode = AudioManager.RINGER_MODE_SILENT
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm?.isNotificationPolicyAccessGranted == true) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "🔇 Đã tắt âm thanh (Chế độ Im lặng)", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (effActionType == "VIBRATE_MODE") {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm?.isNotificationPolicyAccessGranted == true) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "📳 Đã chuyển sang chế độ Rung", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (effActionType == "NORMAL_SOUND") {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm?.isNotificationPolicyAccessGranted == true) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                    val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_RING) ?: 7
                    val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_RING) ?: 0
                    if (currentVol == 0) {
                        audioManager?.setStreamVolume(AudioManager.STREAM_RING, (maxVol * 0.7f).toInt().coerceAtLeast(1), 0)
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "🔔 Đã bật lại chuông âm thanh bình thường", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 4. Tự động Bật / Tắt Bluetooth
            if (effActionType == "BLUETOOTH_ON") {
                try {
                    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val btAdapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                    if (btAdapter != null && !btAdapter.isEnabled) {
                        @Suppress("DEPRECATION")
                        btAdapter.enable()
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "🔵 Đang bật Bluetooth", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (effActionType == "BLUETOOTH_OFF") {
                try {
                    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val btAdapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                    if (btAdapter != null && btAdapter.isEnabled) {
                        @Suppress("DEPRECATION")
                        btAdapter.disable()
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "🔵 Đã tắt Bluetooth", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 5. Mở bảng điều khiển Wi-Fi & Mạng di động nhanh
            if (effActionType == "WIFI_PANEL") {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(panelIntent)
                    } else {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        @Suppress("DEPRECATION")
                        wifiManager?.let { it.isWifiEnabled = !it.isWifiEnabled }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (effActionType == "MOBILE_DATA_PANEL") {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(panelIntent)
                    } else {
                        val dataIntent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(dataIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val noteText = if (isShiftTask) {
                if (effTaskInstruction.isNotBlank()) {
                    "📝 Chi tiết: $effTaskInstruction"
                } else if (effActionType == "OPEN_APP" && effTargetAppName.isNotBlank()) {
                    "🚀 Đến giờ mở $effTargetAppName thực hiện: $effTaskTitle"
                } else {
                    "Đến giờ thực hiện: $effTaskTitle"
                }
            } else {
                when (effActionType) {
                    "OPEN_APP" -> if (effTargetAppName.isNotBlank()) "🚀 Đã đến giờ mở ứng dụng $effTargetAppName! $rawNoteText" else rawNoteText
                    "LOCK_SCREEN" -> "🌙 Đã đến giờ đi ngủ! Hệ thống đã đóng các ứng dụng về màn hình chính. Hãy nghỉ ngơi nhé!"
                    "SILENT_MODE" -> "🔇 Đã chuyển điện thoại sang chế độ Im lặng / Tắt âm thanh theo lịch hẹn."
                    "VIBRATE_MODE" -> "📳 Đã chuyển điện thoại sang chế độ Rung theo lịch hẹn."
                    "NORMAL_SOUND" -> "🔔 Đã bật lại âm thanh chuông điện thoại bình thường theo lịch hẹn."
                    "BLUETOOTH_ON" -> "🔵 Đã kích hoạt bật Bluetooth theo lịch hẹn."
                    "BLUETOOTH_OFF" -> "🔵 Đã tắt kết nối Bluetooth theo lịch hẹn."
                    "WIFI_PANEL" -> "📶 Nhấn để bật/tắt kết nối Wi-Fi nhanh 1 chạm."
                    "MOBILE_DATA_PANEL" -> "🌐 Nhấn để bật/tắt Mạng di động (4G/5G) nhanh 1 chạm."
                    else -> rawNoteText
                }
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val alarmSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channelId = if (effHasLoudAlarm) "notes_reminders_loud_alarm" else "notes_reminders"
            val channelTitle = if (effHasLoudAlarm) "Chuông Báo Thức To (Kho cá & Việc gấp)" else "Nhắc nhở Công việc & Ca làm"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    channelTitle,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = if (effHasLoudAlarm) "Phát chuông báo to cho việc nấu ăn/kho cá/việc gấp chống ngủ quên" else "Hiển thị thông báo nhắc việc theo ca và ghi chú"
                    enableLights(true)
                    enableVibration(true)
                    if (effHasLoudAlarm) {
                        vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800, 400, 1000)
                        val audioAttr = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        setSound(alarmSoundUri, audioAttr)
                    } else {
                        vibrationPattern = longArrayOf(0, 500, 200, 500)
                    }
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Action when notification clicked -> Open MainActivity or Target App
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("LAUNCH_NOTE_ID", noteId)
                if (isQuickNote) {
                    putExtra("IS_QUICK_NOTE", true)
                    putExtra("QUICK_NOTE_ID", noteId)
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                noteId.hashCode(),
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            var launchPendingIntent: PendingIntent? = null
            if (effActionType == "OPEN_APP" && effTargetPackage.isNotBlank()) {
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(effTargetPackage)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    if (launchIntent != null) {
                        launchPendingIntent = PendingIntent.getActivity(
                            context,
                            noteId.hashCode() + 202,
                            launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val primaryContentIntent = if (effActionType == "OPEN_APP" && launchPendingIntent != null) {
                launchPendingIntent
            } else {
                pendingIntent
            }

            val displayTitle = if (isQuickNote) {
                "⚡ [GHI CHÚ NHANH] Nhắc nhở"
            } else if (isRoadmapTask) {
                if (effHasLoudAlarm) "⏰ [LỘ TRÌNH ĐA THÁNG] $categoryName" else "📅 [LỘ TRÌNH] $categoryName"
            } else if (isShiftTask) {
                val icon = if (effHasLoudAlarm) "⏰" else "🔔"
                val timeStr = if (effTimeScheduled.isNotBlank()) "[$effTimeScheduled] " else ""
                val stationSuffix = if (stationName.isNotBlank()) " • $stationName" else ""
                "$icon $timeStr$effTaskTitle$stationSuffix"
            } else {
                when (effActionType) {
                    "OPEN_APP" -> if (effTargetAppName.isNotBlank()) "🚀 [MỞ $effTargetAppName] $categoryName" else "🚀 $categoryName"
                    "LOCK_SCREEN" -> "🌙 [ĐẾN GIỜ ĐI NGỦ] Khóa màn hình & Tắt app"
                    "SILENT_MODE" -> "🔇 [TẮT ÂM THANH] Chế độ Im lặng"
                    "VIBRATE_MODE" -> "📳 [CHUYỂN SANG RUNG] $categoryName"
                    "NORMAL_SOUND" -> "🔔 [BẬT LẠI ÂM THANH] Chuông bình thường"
                    "BLUETOOTH_ON" -> "🔵 [BẬT BLUETOOTH] $categoryName"
                    "BLUETOOTH_OFF" -> "🔵 [TẮT BLUETOOTH] $categoryName"
                    "WIFI_PANEL" -> "📶 [CÀI ĐẶT WI-FI] $categoryName"
                    "MOBILE_DATA_PANEL" -> "🌐 [MẠNG DI ĐỘNG] $categoryName"
                    else -> if (effHasLoudAlarm) "⏰ [CHUÔNG BÁO] $categoryName" else "🔔 $categoryName"
                }
            }

            val bigTextStyle = if (isShiftTask) {
                val fullDetail = buildString {
                    if (effTaskInstruction.isNotBlank()) {
                        append("📖 Hướng dẫn chi tiết:\n$effTaskInstruction")
                    } else {
                        append("Đến giờ thực hiện: $effTaskTitle")
                    }
                    if (effActionType == "OPEN_APP" && effTargetAppName.isNotBlank()) {
                        append("\n\n🚀 Tự động mở ứng dụng: $effTargetAppName")
                    }
                    if (stationName.isNotBlank()) {
                        append("\n📍 Vị trí/Ca: $stationName")
                    }
                }
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(displayTitle)
                    .setSummaryText(if (stationName.isNotBlank()) stationName else "Nhắc ca làm")
                    .bigText(fullDetail)
            } else {
                NotificationCompat.BigTextStyle().bigText(noteText)
            }

            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentTitle(displayTitle)
                .setContentText(noteText)
                .setStyle(bigTextStyle)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(if (effHasLoudAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(primaryContentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)

            if (stationName.isNotBlank()) {
                notificationBuilder.setSubText(stationName)
            }

            if (launchPendingIntent != null) {
                notificationBuilder.setFullScreenIntent(launchPendingIntent, true)
                notificationBuilder.addAction(
                    android.R.drawable.ic_menu_send,
                    "🚀 Mở $effTargetAppName ngay",
                    launchPendingIntent
                )
            }

            if (effActionType == "LOCK_SCREEN") {
                val lockIntent = Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_LOCK_DEVICE
                    putExtra("NOTIFICATION_ID", noteId.hashCode())
                }
                val lockPendingIntent = PendingIntent.getBroadcast(
                    context,
                    noteId.hashCode() + 105,
                    lockIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notificationBuilder.addAction(
                    android.R.drawable.ic_lock_power_off,
                    "🔒 Khóa màn hình",
                    lockPendingIntent
                )
            }

            if (effActionType == "WIFI_PANEL") {
                val wifiIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_WIFI)
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                val wifiPendingIntent = PendingIntent.getActivity(
                    context,
                    noteId.hashCode() + 106,
                    wifiIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notificationBuilder.addAction(
                    android.R.drawable.presence_online,
                    "📶 Bật/Tắt Wi-Fi",
                    wifiPendingIntent
                )
            } else if (effActionType == "MOBILE_DATA_PANEL") {
                val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                } else {
                    Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                }.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                val dataPendingIntent = PendingIntent.getActivity(
                    context,
                    noteId.hashCode() + 107,
                    dataIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notificationBuilder.addAction(
                    android.R.drawable.presence_online,
                    "🌐 Mạng di động 4G/5G",
                    dataPendingIntent
                )
            }

            if (effHasLoudAlarm) {
                notificationBuilder.setSound(alarmSoundUri)
                notificationBuilder.setVibrate(longArrayOf(0, 800, 400, 800, 400, 800, 400, 1000))
                notificationBuilder.setColor(0xFFE53935.toInt())

                // Dismiss alarm action button
                val dismissIntent = Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_DISMISS_ALARM
                    putExtra("NOTIFICATION_ID", noteId.hashCode())
                }
                val dismissPendingIntent = PendingIntent.getBroadcast(
                    context,
                    noteId.hashCode() + 101,
                    dismissIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notificationBuilder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "🛑 Tắt chuông",
                    dismissPendingIntent
                )
                notificationBuilder.setDeleteIntent(dismissPendingIntent)

                // Directly play Alarm sound stream so user will not miss it even when phone is on silent/distracted
                playLoudAlarmSound(context, alarmSoundUri)
            } else {
                notificationBuilder.setDefaults(NotificationCompat.DEFAULT_ALL)
                notificationBuilder.setVibrate(longArrayOf(0, 500, 200, 500))
            }

            notificationManager.notify(noteId.hashCode(), notificationBuilder.build())

            // Tự động lên lịch lại cho ca làm việc vào ngày mai (bao gồm cả báo trước 1 phút nếu có)
            val effTime = if (effTimeScheduled.isNotBlank()) effTimeScheduled else (intent.getStringExtra("TIME_SCHEDULED") ?: "")
            val effStId = activeDbTask?.stationId ?: intent.getStringExtra("STATION_ID") ?: ""
            if (isShiftTask && effTime.isNotBlank()) {
                val taskToReschedule = activeDbTask ?: com.example.data.ShiftTask(
                    id = noteId,
                    stationId = effStId,
                    timeScheduled = effTime,
                    title = effTaskTitle,
                    instruction = effTaskInstruction,
                    hasLoudAlarm = effHasLoudAlarm,
                    actionType = effActionType,
                    targetPackageName = effTargetPackage,
                    targetAppName = effTargetAppName
                )
                com.example.utils.ShiftAlarmHelper.scheduleShiftTaskAlarm(
                    context = context,
                    task = taskToReschedule,
                    stationName = stationName,
                    forceTomorrow = true
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun playLoudAlarmSound(context: Context, soundUri: Uri) {
        try {
            stopAlarm()
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, soundUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone?.play()
            activeRingtone = ringtone

            // Automatically stop alarm playback after 20 seconds so it doesn't drain battery indefinitely
            CoroutineScope(Dispatchers.Default).launch {
                delay(20_000)
                stopAlarm()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val ACTION_DISMISS_ALARM = "com.example.ACTION_DISMISS_ALARM"
        const val ACTION_LOCK_DEVICE = "com.example.ACTION_LOCK_DEVICE"
        const val ACTION_EXECUTE_AUTOMATION_NOW = "com.example.ACTION_EXECUTE_AUTOMATION_NOW"
        const val ACTION_CANCEL_AUTOMATION = "com.example.ACTION_CANCEL_AUTOMATION"
        const val IS_AUTOMATION_PRE_WARNING = "IS_AUTOMATION_PRE_WARNING"

        fun isEmulator(): Boolean {
            return (Build.FINGERPRINT.startsWith("generic")
                    || Build.FINGERPRINT.startsWith("unknown")
                    || Build.MODEL.contains("google_sdk")
                    || Build.MODEL.contains("Emulator")
                    || Build.MODEL.contains("Android SDK built for x86")
                    || Build.MANUFACTURER.contains("Genymotion")
                    || Build.HARDWARE.contains("goldfish")
                    || Build.HARDWARE.contains("ranchu")
                    || Build.PRODUCT.contains("sdk")
                    || Build.PRODUCT.contains("google_sdk")
                    || Build.PRODUCT.contains("sdk_x86")
                    || Build.PRODUCT.contains("vbox86p")
                    || Build.PRODUCT.contains("emulator")
                    || Build.PRODUCT.contains("simulator"))
        }

        @Volatile
        private var activeRingtone: Ringtone? = null

        fun stopAlarm() {
            try {
                activeRingtone?.stop()
                activeRingtone = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun sendNotificationNow(context: Context, title: String, text: String, hasLoudAlarm: Boolean = false) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

                val channelId = if (hasLoudAlarm) "notes_reminders_loud_alarm" else "notes_reminders"
                val channelTitle = if (hasLoudAlarm) "Chuông Báo Thức To (Kho cá & Việc gấp)" else "Nhắc nhở Công việc & Ca làm"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        channelTitle,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Hiển thị thông báo nhắc việc theo ca và ghi chú"
                        enableLights(true)
                        enableVibration(true)
                        vibrationPattern = if (hasLoudAlarm) longArrayOf(0, 800, 400, 800, 400, 800) else longArrayOf(0, 500, 200, 500)
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    System.currentTimeMillis().toInt(),
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                    .setContentTitle(if (hasLoudAlarm) "⏰ $title" else "🔔 $title")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(if (hasLoudAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(if (hasLoudAlarm) longArrayOf(0, 800, 400, 800, 400, 800) else longArrayOf(0, 500, 200, 500))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build()

                notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
