package com.example.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.AppDatabase
import com.example.data.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val notes = db.noteDao().getFutureReminders(System.currentTimeMillis())
                    
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    for (note in notes) {
                        val triggerTimeMs = note.reminderTime ?: continue
                        val alarmIntent = Intent(context, ReminderReceiver::class.java).apply {
                            putExtra("NOTE_ID", note.id)
                            putExtra("NOTE_TEXT", note.text)
                            putExtra("CATEGORY_NAME", note.categoryName)
                        }
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            note.id.hashCode(),
                            alarmIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTimeMs,
                                    pendingIntent
                                )
                            } else {
                                alarmManager.setExact(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTimeMs,
                                    pendingIntent
                                )
                            }
                        } catch (e: SecurityException) {
                            alarmManager.set(
                                AlarmManager.RTC_WAKEUP,
                                triggerTimeMs,
                                pendingIntent
                            )
                        }
                    }

                    // Reschedule future quick note reminders
                    val quickNotes = db.quickNoteDao().getFutureQuickNoteReminders(System.currentTimeMillis())
                    for (qn in quickNotes) {
                        val triggerTimeMs = qn.reminderTime ?: continue
                        val alarmIntent = Intent(context, ReminderReceiver::class.java).apply {
                            putExtra("NOTE_ID", qn.id)
                            putExtra("NOTE_TEXT", qn.text)
                            putExtra("CATEGORY_NAME", "Ghi chú nhanh")
                            putExtra("IS_QUICK_NOTE", true)
                        }
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            qn.id.hashCode() + 5001,
                            alarmIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTimeMs,
                                    pendingIntent
                                )
                            } else {
                                alarmManager.setExact(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTimeMs,
                                    pendingIntent
                                )
                            }
                        } catch (e: SecurityException) {
                            alarmManager.set(
                                AlarmManager.RTC_WAKEUP,
                                triggerTimeMs,
                                pendingIntent
                            )
                        }
                    }

                    // Reschedule shift tasks only if shift is currently active
                    val prefs = context.getSharedPreferences("notes_app_prefs", Context.MODE_PRIVATE)
                    val isShiftStarted = prefs.getBoolean("is_shift_started", false)
                    val selectedStationId = prefs.getString("selected_station_id", null)
                    if (isShiftStarted) {
                        val shiftTasks = db.shiftDao().getUncompletedTasksDirect()
                        for (task in shiftTasks) {
                            if (task.isHidden) continue
                            if (selectedStationId != null && task.stationId != selectedStationId) {
                                continue
                            }
                            val parts = task.timeScheduled.split(":")
                            if (parts.size >= 2) {
                                val hour = parts[0].trim().toIntOrNull() ?: continue
                                val minute = parts[1].trim().toIntOrNull() ?: continue
                                val calendar = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                                    set(java.util.Calendar.MINUTE, minute)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }
                                var triggerTimeMs = calendar.timeInMillis
                                val now = System.currentTimeMillis()
                                if (triggerTimeMs < now - 30_000) {
                                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                    triggerTimeMs = calendar.timeInMillis
                                }

                                val noteDetail = if (task.instruction.isNotBlank()) "📝 Chi tiết: ${task.instruction}" else "📋 Công việc: ${task.title}"
                                val alarmIntent = Intent(context, ReminderReceiver::class.java).apply {
                                    putExtra("NOTE_ID", task.id)
                                    putExtra("TASK_TITLE", task.title)
                                    putExtra("TASK_INSTRUCTION", task.instruction)
                                    putExtra("NOTE_TEXT", noteDetail)
                                    putExtra("CATEGORY_NAME", if (task.hasLoudAlarm) "Chuông báo việc (${task.timeScheduled})" else "Nhắc ca làm (${task.timeScheduled})")
                                    putExtra("IS_SHIFT_TASK", true)
                                    putExtra("STATION_ID", task.stationId)
                                    putExtra("TIME_SCHEDULED", task.timeScheduled)
                                    putExtra("HAS_LOUD_ALARM", task.hasLoudAlarm)
                                    putExtra("ACTION_TYPE", task.actionType)
                                    putExtra("TARGET_PACKAGE_NAME", task.targetPackageName)
                                    putExtra("TARGET_APP_NAME", task.targetAppName)
                                }
                                val pendingIntent = PendingIntent.getBroadcast(
                                    context,
                                    task.id.hashCode(),
                                    alarmIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )

                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        alarmManager.setExactAndAllowWhileIdle(
                                            AlarmManager.RTC_WAKEUP,
                                            triggerTimeMs,
                                            pendingIntent
                                        )
                                    } else {
                                        alarmManager.setExact(
                                            AlarmManager.RTC_WAKEUP,
                                            triggerTimeMs,
                                            pendingIntent
                                        )
                                    }
                                } catch (e: SecurityException) {
                                    alarmManager.set(
                                        AlarmManager.RTC_WAKEUP,
                                        triggerTimeMs,
                                        pendingIntent
                                    )
                                }
                            }
                        }
                    }

                    // Reschedule shift rotation check if enabled
                    val rotationConfig = com.example.utils.ShiftRotationManager.loadConfig(context)
                    if (rotationConfig.isEnabled) {
                        com.example.utils.ShiftRotationManager.applyRotationForToday(context)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
