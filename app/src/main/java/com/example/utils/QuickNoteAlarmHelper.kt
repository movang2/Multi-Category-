package com.example.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.QuickNoteWidgetProvider
import com.example.data.AppDatabase
import com.example.data.QuickNote
import com.example.receivers.ReminderReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QuickNoteAlarmHelper {

    fun scheduleAlarm(context: Context, note: QuickNote) {
        val triggerTimeMs = note.reminderTime ?: return
        val now = System.currentTimeMillis()
        if (triggerTimeMs <= now) return

        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("NOTE_ID", note.id)
                putExtra("NOTE_TEXT", note.text)
                putExtra("CATEGORY_NAME", "Ghi chú nhanh")
                putExtra("IS_QUICK_NOTE", true)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                note.id.hashCode() + 5001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelAlarm(context: Context, noteId: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                noteId.hashCode() + 5001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateWidget(context: Context) {
        try {
            val intent = Intent(context, QuickNoteWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context.applicationContext)
                .getAppWidgetIds(ComponentName(context.applicationContext, QuickNoteWidgetProvider::class.java))
            if (ids != null && ids.isNotEmpty()) {
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveOrUpdateNote(
        context: Context,
        note: QuickNote,
        previousNoteId: String? = null
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        if (previousNoteId != null && previousNoteId != note.id) {
            cancelAlarm(context, previousNoteId)
        } else {
            cancelAlarm(context, note.id)
        }

        db.quickNoteDao().insertQuickNote(note)

        if (note.hasNotification && note.reminderTime != null && note.reminderTime > System.currentTimeMillis()) {
            scheduleAlarm(context, note)
        }
        updateWidget(context)
    }

    suspend fun rearmAlarm(
        context: Context,
        note: QuickNote,
        addMinutes: Int
    ) = withContext(Dispatchers.IO) {
        val newReminderTime = System.currentTimeMillis() + (addMinutes * 60 * 1000L)
        val updatedNote = note.copy(
            hasNotification = true,
            reminderTime = newReminderTime,
            isNotificationTriggered = false,
            updatedAt = System.currentTimeMillis()
        )
        saveOrUpdateNote(context, updatedNote)
        updatedNote
    }
}
