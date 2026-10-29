package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuickNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, QuickNoteWidgetProvider::class.java)
            )
            if (ids != null && ids.isNotEmpty()) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (id in ids) {
                            updateAppWidget(context, appWidgetManager, id)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        suspend fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_note)

            // Intent to add new note (Tab 0)
            val newNoteIntent = Intent(context, QuickNoteWidgetActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(QuickNoteWidgetActivity.EXTRA_TAB, 0)
            }
            val newNotePendingIntent = PendingIntent.getActivity(
                context,
                101,
                newNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Intent to manage notes and reminders (Tab 1)
            val manageIntent = Intent(context, QuickNoteWidgetActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(QuickNoteWidgetActivity.EXTRA_TAB, 1)
            }
            val managePendingIntent = PendingIntent.getActivity(
                context,
                102,
                manageIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.btn_add_container, newNotePendingIntent)
            views.setOnClickPendingIntent(R.id.btn_manage_container, managePendingIntent)
            views.setOnClickPendingIntent(R.id.header, managePendingIntent)
            views.setOnClickPendingIntent(R.id.widget_prompt, managePendingIntent)

            // Query database for latest note & upcoming reminders to display on the widget
            try {
                val db = AppDatabase.getDatabase(context)
                val allNotes = db.quickNoteDao().getAllQuickNotesDirect()
                val now = System.currentTimeMillis()
                val futureReminders = allNotes.filter {
                    it.hasNotification && it.reminderTime != null && it.reminderTime > now && !it.isNotificationTriggered
                }.sortedBy { it.reminderTime }

                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                val prefs = context.getSharedPreferences("notes_app_prefs", Context.MODE_PRIVATE)
                val hideContent = prefs.getBoolean("hide_widget_note_content", true)

                if (hideContent) {
                    if (futureReminders.isNotEmpty()) {
                        val nextReminder = futureReminders.first()
                        val timeStr = timeFormat.format(Date(nextReminder.reminderTime ?: now))
                        views.setTextViewText(R.id.widget_badge, "🔔 $timeStr")
                        views.setTextViewText(
                            R.id.widget_prompt,
                            "⏰ Có lời nhắc lúc $timeStr • Chạm để mở"
                        )
                    } else if (allNotes.isNotEmpty()) {
                        views.setTextViewText(R.id.widget_badge, "(${allNotes.size} ghi chú)")
                        views.setTextViewText(
                            R.id.widget_prompt,
                            "🔒 Nội dung được bảo mật • Chạm để mở"
                        )
                    } else {
                        views.setTextViewText(R.id.widget_badge, "")
                        views.setTextViewText(
                            R.id.widget_prompt,
                            "Chạm để viết ghi chú nhanh mới..."
                        )
                    }
                } else {
                    if (futureReminders.isNotEmpty()) {
                        val nextReminder = futureReminders.first()
                        val timeStr = timeFormat.format(Date(nextReminder.reminderTime ?: now))
                        views.setTextViewText(R.id.widget_badge, "🔔 $timeStr")
                        views.setTextViewText(
                            R.id.widget_prompt,
                            "⏰ [Báo lúc $timeStr]: ${nextReminder.text.replace("\n", " ")}"
                        )
                    } else if (allNotes.isNotEmpty()) {
                        val latest = allNotes.first()
                        views.setTextViewText(R.id.widget_badge, "(${allNotes.size} ghi chú)")
                        views.setTextViewText(
                            R.id.widget_prompt,
                            latest.text.replace("\n", " ")
                        )
                    } else {
                        views.setTextViewText(R.id.widget_badge, "")
                        views.setTextViewText(
                            R.id.widget_prompt,
                            "Chạm để viết ghi chú nhanh mới..."
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
