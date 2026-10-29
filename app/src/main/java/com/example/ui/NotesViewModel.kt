package com.example.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.receivers.AppDeviceAdminReceiver
import com.example.receivers.ReminderReceiver
import com.example.data.Category
import com.example.data.Note
import com.example.data.QuickNote
import com.example.data.ShiftStation
import com.example.data.ShiftTask
import com.example.data.ShiftHandover
import com.example.data.NoteRepository
import com.example.data.AppDatabase
import com.example.utils.StorageUtils
import com.example.ai.AiScheduleService
import com.example.ai.AiScheduleStep
import com.example.ai.AiSchedulePlan
import com.example.ai.AiChatMessage
import com.example.data.Roadmap
import com.example.data.RoadmapTask
import com.example.ai.AiRoadmapPlan
import com.example.ai.AiRoadmapChatMessage
import com.example.ai.AiRoadmapTaskStep
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.utils.QuickNoteAlarmHelper
import com.example.data.ShiftRotationConfig
import com.example.data.ShiftDayAssignment
import com.example.utils.ShiftRotationManager
import com.example.ai.AiRotationPlan

enum class SortOrder {
    NEWEST,
    OLDEST
}

enum class QuickSortOrder {
    CREATED_DESC,
    CREATED_ASC,
    UPDATED_DESC,
    UPDATED_ASC
}

class NotesViewModel(val repository: NoteRepository, val appContext: Context? = null) : ViewModel() {

    private val prefs = appContext?.getSharedPreferences("notes_app_prefs", Context.MODE_PRIVATE)

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appCtx = context.applicationContext
                val db = AppDatabase.getDatabase(appCtx)
                val repo = NoteRepository(db.noteDao(), db.quickNoteDao(), db.shiftDao(), db.roadmapDao())
                @Suppress("UNCHECKED_CAST")
                return NotesViewModel(repo, appCtx) as T
            }
        }
    }

    init {
        viewModelScope.launch {
            try {
                repository.removeLegacySampleData()
                val existing = repository.getAllShiftStationsDirect()
                if (existing.isEmpty()) {
                    repository.seedDefaultShiftDataIfEmpty(false)
                }
                prefs?.edit()?.putBoolean("has_seeded_shift_data_v3", true)?.apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val allNotesList = MutableStateFlow<List<Note>>(emptyList())

    init {
        viewModelScope.launch {
            repository.allNotes.collect {
                allNotesList.value = it
            }
        }
    }

    // --- State Observables ---
    val categories: StateFlow<List<Category>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedCategory = MutableStateFlow<Category?>(null)
    val searchQuery = MutableStateFlow("")
    val filterTag = MutableStateFlow<String?>(null)
    val sortOrder = MutableStateFlow(SortOrder.NEWEST)

    val quickSearchQuery = MutableStateFlow("")
    val quickSortOrder = MutableStateFlow(QuickSortOrder.CREATED_DESC)

    val allQuickNotesList: StateFlow<List<QuickNote>> = repository.allQuickNotes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val targetQuickNoteId = MutableStateFlow<String?>(null)
    fun setTargetQuickNoteId(id: String?) {
        targetQuickNoteId.value = id
    }

    val filteredQuickNotes: StateFlow<List<QuickNote>> = combine(
        repository.allQuickNotes,
        quickSearchQuery,
        quickSortOrder
    ) { notes, query, order ->
        var list = notes

        if (query.isNotBlank()) {
            list = list.filter { it.text.contains(query, ignoreCase = true) }
        }

        list.sortedWith { a, b ->
            if (a.pinned != b.pinned) {
                if (a.pinned) -1 else 1
            } else {
                when (order) {
                    QuickSortOrder.CREATED_DESC -> b.createdAt.compareTo(a.createdAt)
                    QuickSortOrder.CREATED_ASC -> a.createdAt.compareTo(b.createdAt)
                    QuickSortOrder.UPDATED_DESC -> b.updatedAt.compareTo(a.updatedAt)
                    QuickSortOrder.UPDATED_ASC -> a.updatedAt.compareTo(b.updatedAt)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined notes with intelligent search filter, tags filter, category filter, and sorting
    val filteredNotes: StateFlow<List<Note>> = combine(
        repository.allNotes,
        selectedCategory,
        searchQuery,
        filterTag,
        sortOrder
    ) { notes, currentCategory, query, tag, order ->
        var list = notes

        // 1. Filter by category
        if (currentCategory != null) {
            list = list.filter { it.categoryName.equals(currentCategory.name, ignoreCase = true) }
        }

        // 2. Filter by search query (text or tag search)
        if (query.isNotBlank()) {
            list = list.filter {
                it.text.contains(query, ignoreCase = true) ||
                        it.tags.any { t -> t.contains(query, ignoreCase = true) }
            }
        }

        // 3. Filter by selected chip tag
        if (tag != null) {
            list = list.filter { it.tags.any { t -> t.equals(tag, ignoreCase = true) } }
        }

        // 4. Sort: Pinned notes are ALWAYS kept on top, then sorted according to order preference
        list.sortedWith { a, b ->
            if (a.pinned != b.pinned) {
                if (a.pinned) -1 else 1
            } else {
                when (order) {
                    SortOrder.NEWEST -> b.createdAt.compareTo(a.createdAt)
                    SortOrder.OLDEST -> a.createdAt.compareTo(b.createdAt)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All distinct tags mined from existing notes to provide quick-filter options
    val allTags: StateFlow<List<String>> = repository.allNotes
        .map { notes ->
            notes.flatMap { it.tags }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Actions ---

    fun selectCategory(category: Category?) {
        selectedCategory.value = category
        // Clear active tag filter when category changes
        filterTag.value = null
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertCategory(Category(name = name))
            if (selectedCategory.value == null) {
                selectedCategory.value = Category(name = name)
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            if (selectedCategory.value?.name == category.name) {
                selectedCategory.value = null
            }
        }
    }

    fun renameCategory(oldName: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameCategory(oldName, newName)
            if (selectedCategory.value?.name == oldName) {
                selectedCategory.value = Category(name = newName)
            }
        }
    }

    private fun cancelReminderAlarm(context: Context, noteId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun saveNote(
        context: Context,
        id: String? = null,
        categoryName: String,
        text: String,
        images: List<String>,
        pinned: Boolean,
        isLocked: Boolean = false,
        passwordHash: String? = null,
        voicePath: String? = null,
        audioDurationMs: Long = 0L,
        reminderTime: Long? = null
    ) {
        viewModelScope.launch {
            // Mine tags automatically from the written note text (extract words starting with #)
            val textTags = mutableListOf<String>()
            val pattern = Regex("#([a-zA-Z0-9_À-ỹ]+)")
            pattern.findAll(text).forEach { match ->
                textTags.add(match.groupValues[1])
            }

            val oldNote = id?.let { repository.getNoteById(it) }

            val note = Note(
                id = id ?: java.util.UUID.randomUUID().toString(),
                categoryName = categoryName,
                text = text,
                images = images,
                tags = textTags.distinct(),
                pinned = pinned,
                createdAt = oldNote?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                reminderTime = reminderTime,
                isLocked = isLocked,
                passwordHash = passwordHash,
                voicePath = voicePath,
                audioDurationMs = audioDurationMs
            )

            // 1. Cancel previous alarm if any
            cancelReminderAlarm(context, note.id)

            // 2. Save note to DB
            repository.insertNote(note)

            // 3. Schedule new alarm if reminderTime is present and in the future
            if (reminderTime != null && reminderTime > System.currentTimeMillis()) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("NOTE_ID", note.id)
                    putExtra("NOTE_TEXT", note.text)
                    putExtra("CATEGORY_NAME", note.categoryName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    note.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            reminderTime,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            reminderTime,
                            pendingIntent
                        )
                    }
                } catch (e: SecurityException) {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                    )
                }
            }
        }
    }

    fun deleteNote(context: Context, note: Note) {
        viewModelScope.launch {
            cancelReminderAlarm(context, note.id)
            repository.deleteNote(note)
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(pinned = !note.pinned, updatedAt = System.currentTimeMillis())
            repository.insertNote(updated)
        }
    }

    fun toggleLock(note: Note, passwordPin: String?) {
        viewModelScope.launch {
            val updated = note.copy(
                isLocked = passwordPin != null,
                passwordHash = passwordPin,
                updatedAt = System.currentTimeMillis()
            )
            repository.insertNote(updated)
        }
    }

    // --- Reminders & Notifications Scheduling ---

    fun scheduleReminder(context: Context, note: Note, triggerTimeMs: Long) {
        val now = System.currentTimeMillis()
        if (triggerTimeMs <= now + 1000) {
            // If scheduled for immediate/past, trigger notification right now
            ReminderReceiver.sendNotificationNow(context, "Nhắc nhở: ${note.categoryName}", note.text)
        } else {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("NOTE_ID", note.id)
                putExtra("NOTE_TEXT", note.text)
                putExtra("CATEGORY_NAME", note.categoryName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                note.id.hashCode(),
                intent,
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
                // Fallback for permission errors on API 31+ exact alarm
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
        }

        viewModelScope.launch {
            val updated = note.copy(reminderTime = triggerTimeMs, updatedAt = System.currentTimeMillis())
            repository.insertNote(updated)
        }
    }

    fun triggerTestNotification(context: Context, title: String = "Nhắc nhở ca làm việc", message: String = "Ứng dụng vừa gửi thông báo thử nghiệm thành công trên Android!") {
        ReminderReceiver.sendNotificationNow(context, title, message)
    }

    fun clearReminder(context: Context, note: Note) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            note.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        viewModelScope.launch {
            val updated = note.copy(reminderTime = null, updatedAt = System.currentTimeMillis())
            repository.insertNote(updated)
        }
    }

    // --- JSON Backup Imports ---

    fun importBackup(jsonText: String) {
        viewModelScope.launch {
            try {
                val (importedCategories, importedNotes) = StorageUtils.importDataFromJson(jsonText)
                for (cat in importedCategories) {
                    repository.insertCategory(cat)
                }
                for (nt in importedNotes) {
                    repository.insertNote(nt)
                }
                if (selectedCategory.value == null && importedCategories.isNotEmpty()) {
                    selectedCategory.value = importedCategories.first()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importBackupFromZip(context: Context, backupData: com.example.utils.ImportedZipData) {
        viewModelScope.launch {
            try {
                for (cat in backupData.categories) {
                    repository.insertCategory(cat)
                }
                for (nt in backupData.notes) {
                    repository.insertNote(nt)
                }
                for (qn in backupData.quickNotes) {
                    repository.insertQuickNote(qn)
                }
                for (st in backupData.shiftStations) {
                    repository.insertStation(st)
                }
                for (task in backupData.shiftTasks) {
                    repository.insertTask(task)
                    if (isShiftStarted.value && !task.isCompleted && task.stationId == selectedStationId.value) {
                        scheduleShiftTaskAlarm(context, task)
                    }
                }
                for (ho in backupData.shiftHandovers) {
                    repository.insertHandover(ho)
                }
                if (selectedCategory.value == null && backupData.categories.isNotEmpty()) {
                    selectedCategory.value = backupData.categories.first()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- QUICK NOTES ---
    fun saveQuickNote(
        context: Context? = null,
        id: String? = null,
        text: String,
        pinned: Boolean = false,
        hasNotification: Boolean = false,
        reminderTime: Long? = null
    ) {
        viewModelScope.launch {
            val oldNote = id?.let { repository.getQuickNoteById(it) }
            val qnId = id ?: oldNote?.id ?: java.util.UUID.randomUUID().toString()

            if (context != null && oldNote != null && oldNote.hasNotification) {
                cancelQuickNoteAlarm(context, oldNote.id)
            }

            val validReminder = hasNotification && reminderTime != null
            val isTriggered = if (validReminder && reminderTime != null && reminderTime > System.currentTimeMillis()) {
                false
            } else {
                oldNote?.isNotificationTriggered ?: false
            }

            val quickNote = QuickNote(
                id = qnId,
                text = text,
                pinned = pinned,
                createdAt = oldNote?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                hasNotification = validReminder,
                reminderTime = if (validReminder) reminderTime else null,
                isNotificationTriggered = isTriggered
            )
            repository.insertQuickNote(quickNote)

            if (context != null && validReminder && reminderTime != null && reminderTime > System.currentTimeMillis()) {
                scheduleQuickNoteAlarm(context, quickNote)
            }
            if (context != null) {
                QuickNoteAlarmHelper.updateWidget(context)
            }
        }
    }

    fun deleteQuickNote(context: Context? = null, quickNote: QuickNote) {
        viewModelScope.launch {
            if (context != null && quickNote.hasNotification) {
                cancelQuickNoteAlarm(context, quickNote.id)
            }
            repository.deleteQuickNote(quickNote)
            if (context != null) {
                QuickNoteAlarmHelper.updateWidget(context)
            }
        }
    }

    fun togglePinQuickNote(quickNote: QuickNote) {
        togglePinQuickNote(null, quickNote)
    }

    fun togglePinQuickNote(context: Context? = null, quickNote: QuickNote) {
        viewModelScope.launch {
            repository.insertQuickNote(
                quickNote.copy(
                    pinned = !quickNote.pinned,
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (context != null) {
                QuickNoteAlarmHelper.updateWidget(context)
            }
        }
    }

    fun scheduleQuickNoteAlarm(context: Context, note: QuickNote) {
        QuickNoteAlarmHelper.scheduleAlarm(context, note)
    }

    fun cancelQuickNoteAlarm(context: Context, noteId: String) {
        QuickNoteAlarmHelper.cancelAlarm(context, noteId)
    }

    fun updateQuickNoteReminder(context: Context, note: QuickNote, newReminderTime: Long?, enabled: Boolean) {
        viewModelScope.launch {
            cancelQuickNoteAlarm(context, note.id)
            val valid = enabled && newReminderTime != null
            val updated = note.copy(
                hasNotification = valid,
                reminderTime = if (valid) newReminderTime else null,
                isNotificationTriggered = false,
                updatedAt = System.currentTimeMillis()
            )
            repository.insertQuickNote(updated)
            if (valid && newReminderTime != null && newReminderTime > System.currentTimeMillis()) {
                scheduleQuickNoteAlarm(context, updated)
            }
            QuickNoteAlarmHelper.updateWidget(context)
        }
    }

    // --- SHIFT & HANDOVER MANAGEMENT ---
    val allShiftStations: StateFlow<List<ShiftStation>> = repository.allShiftStations
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allShiftTasks: StateFlow<List<ShiftTask>> = repository.allShiftTasks
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allShiftHandovers: StateFlow<List<ShiftHandover>> = repository.allShiftHandovers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val legacyStationIds = setOf("station_may_a", "station_may_b", "station_dong_goi", "station_ve_sinh", "station_kiem_tra")

    val selectedStationId = MutableStateFlow<String?>(
        prefs?.getString("selected_station_id", null)?.takeIf { it !in legacyStationIds }
    )
    val startedStationIds = MutableStateFlow<Set<String>>(
        (prefs?.getStringSet("started_station_ids", null)?.toSet() ?: emptySet())
            .filter { it !in legacyStationIds }.toSet()
    )
    val isShiftStarted = MutableStateFlow(startedStationIds.value.isNotEmpty())
    val activeShiftTime = MutableStateFlow(prefs?.getString("active_shift_time", "08:00 - 20:00") ?: "08:00 - 20:00")

    val selectedStation: StateFlow<ShiftStation?> = combine(allShiftStations, selectedStationId) { stations, id ->
        stations.find { it.id == id } ?: stations.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasksForSelectedStation: StateFlow<List<ShiftTask>> = selectedStation
        .flatMapLatest { station ->
            if (station != null) repository.getTasksForStation(station.id) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val handoversForSelectedStation: StateFlow<List<ShiftHandover>> = selectedStation
        .flatMapLatest { station ->
            if (station != null) repository.getHandoversForStation(station.id) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            try {
                allShiftStations.collect { stations ->
                    if (stations.isNotEmpty()) {
                        val currentId = selectedStationId.value
                        if (currentId == null || stations.none { it.id == currentId }) {
                            val firstId = stations.first().id
                            selectedStationId.value = firstId
                            prefs?.edit()?.putString("selected_station_id", firstId)?.apply()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isStationStarted(stationId: String): Boolean {
        return startedStationIds.value.contains(stationId)
    }

    fun selectStation(stationId: String, context: Context? = null) {
        selectedStationId.value = stationId
        prefs?.edit()?.putString("selected_station_id", stationId)?.apply()
    }

    fun startShiftForStation(stationId: String, shiftTime: String = "08:00 - 20:00", context: Context? = null, resetCompleted: Boolean = false) {
        val newSet = startedStationIds.value + stationId
        startedStationIds.value = newSet
        isShiftStarted.value = true
        activeShiftTime.value = shiftTime
        prefs?.edit()
            ?.putStringSet("started_station_ids", HashSet(newSet))
            ?.putBoolean("is_shift_started", true)
            ?.putString("active_shift_time", shiftTime)
            ?.commit()

        val ctx = context ?: appContext
        if (ctx != null) {
            viewModelScope.launch {
                // Đọc trực tiếp từ cơ sở dữ liệu SQLite để bảo đảm lấy đầy đủ thông tin tiêu đề và hướng dẫn chi tiết mới nhất
                val tasks = repository.getTasksForStationDirect(stationId)
                val shouldReset = resetCompleted || (tasks.isNotEmpty() && tasks.all { it.isCompleted || it.isHidden })
                val finalTasks = if (shouldReset) {
                    tasks.map { task ->
                        if (task.isCompleted) {
                            val reset = task.copy(isCompleted = false, completedAt = null)
                            repository.updateTask(reset)
                            reset
                        } else task
                    }
                } else {
                    tasks
                }

                finalTasks.filter { !it.isHidden && !it.isCompleted }.forEach { task ->
                    scheduleShiftTaskAlarm(ctx, task)
                }
            }
        }
    }

    fun endShiftForStation(stationId: String, context: Context? = null) {
        val newSet = startedStationIds.value - stationId
        startedStationIds.value = newSet
        isShiftStarted.value = newSet.isNotEmpty()
        prefs?.edit()
            ?.putStringSet("started_station_ids", HashSet(newSet))
            ?.putBoolean("is_shift_started", newSet.isNotEmpty())
            ?.commit()

        val ctx = context ?: appContext
        if (ctx != null) {
            viewModelScope.launch {
                val dbTasks = repository.getTasksForStation(stationId).firstOrNull() ?: allShiftTasks.value.filter { it.stationId == stationId }
                dbTasks.forEach { task ->
                    cancelShiftTaskAlarm(ctx, task)
                }
            }
        }
    }

    fun startShift(shiftTime: String = "08:00 - 20:00", context: Context? = null) {
        selectedStationId.value?.let { stId ->
            startShiftForStation(stId, shiftTime, context)
        }
    }

    fun endShift(context: Context? = null) {
        selectedStationId.value?.let { stId ->
            endShiftForStation(stId, context)
        }
    }

    fun toggleTaskHidden(context: Context, task: ShiftTask) {
        viewModelScope.launch {
            val updated = task.copy(isHidden = !task.isHidden)
            repository.updateTask(updated)
            if (updated.isHidden) {
                // Khi người dùng ấn ẨN: HỦY thông báo ngay lập tức, không cho phép báo thức bất kể có bắt đầu ca hay không
                cancelShiftTaskAlarm(context, updated)
            } else {
                // Khi người dùng ấn HIỆN: Nếu ca làm đang được Bắt đầu thì phát thông báo bình thường như các việc khác
                if (startedStationIds.value.contains(task.stationId) && !updated.isCompleted) {
                    scheduleShiftTaskAlarm(context, updated)
                }
            }
        }
    }

    fun toggleTaskCompletion(context: Context, task: ShiftTask) {
        viewModelScope.launch {
            val updated = task.copy(
                isCompleted = !task.isCompleted,
                completedAt = if (!task.isCompleted) System.currentTimeMillis() else null
            )
            repository.updateTask(updated)
            if (updated.isCompleted) {
                cancelShiftTaskAlarm(context, updated)
            } else if (startedStationIds.value.contains(task.stationId) && !updated.isHidden) {
                scheduleShiftTaskAlarm(context, updated)
            }
        }
    }

    fun parseHourMinute(timeStr: String): Pair<Int, Int>? {
        val clean = timeStr.trim().replace("h", ":").replace("H", ":").replace(".", ":").replace("-", ":")
        val parts = clean.split(":")
        if (parts.size >= 2) {
            val h = parts[0].trim().toIntOrNull() ?: return null
            val m = parts[1].trim().toIntOrNull() ?: return null
            if (h in 0..23 && m in 0..59) {
                return Pair(h, m)
            }
        }
        return null
    }

    fun saveShiftTask(
        context: Context,
        stationId: String,
        taskId: String? = null,
        timeScheduled: String,
        title: String,
        instruction: String,
        priority: String,
        requiresConfirmation: Boolean,
        isCompleted: Boolean = false,
        completedAt: Long? = null,
        hasLoudAlarm: Boolean = false,
        isHidden: Boolean = false,
        actionType: String = "",
        targetPackageName: String = "",
        targetAppName: String = ""
    ) {
        viewModelScope.launch {
            val parsed = parseHourMinute(timeScheduled)
            val normalizedTime = if (parsed != null) {
                String.format(java.util.Locale.getDefault(), "%02d:%02d", parsed.first, parsed.second)
            } else {
                timeScheduled.trim()
            }

            val task = ShiftTask(
                id = taskId ?: java.util.UUID.randomUUID().toString(),
                stationId = stationId,
                timeScheduled = normalizedTime,
                title = title,
                instruction = instruction,
                priority = priority,
                requiresConfirmation = requiresConfirmation,
                isCompleted = isCompleted,
                completedAt = completedAt,
                hasLoudAlarm = hasLoudAlarm,
                isHidden = isHidden,
                actionType = actionType,
                targetPackageName = targetPackageName,
                targetAppName = targetAppName
            )
            repository.insertTask(task)
            
            // Đảm bảo chọn vị trí hiện tại để danh sách cập nhật hiển thị ngay lập tức
            selectedStationId.value = stationId
            prefs?.edit()?.putString("selected_station_id", stationId)?.apply()

            // Chỉ lên lịch thông báo nếu ca làm của vị trí này đang được BẮT ĐẦU VÀ công việc KHÔNG bị ẨN
            if (startedStationIds.value.contains(stationId) && !task.isHidden && !task.isCompleted) {
                scheduleShiftTaskAlarm(context, task)
            }
        }
    }

    fun scheduleShiftTaskAlarm(context: Context, task: ShiftTask) {
        if (task.isHidden || task.isCompleted) return

        val parsed = parseHourMinute(task.timeScheduled) ?: return
        val hour = parsed.first
        val minute = parsed.second

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        var triggerTimeMs = calendar.timeInMillis
        val now = System.currentTimeMillis()

        // If time has passed today by more than 5s, schedule for tomorrow
        if (triggerTimeMs < now - 5_000) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            triggerTimeMs = calendar.timeInMillis
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val noteDetail = if (task.instruction.isNotBlank()) "📝 Chi tiết: ${task.instruction}" else "📋 Công việc: ${task.title}"
        val intent = Intent(context, ReminderReceiver::class.java).apply {
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
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val showIntent = Intent(context, MainActivity::class.java)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                task.id.hashCode(),
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: Exception) {
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
            } catch (e2: Exception) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
        }
    }

    fun cancelShiftTaskAlarm(context: Context, task: ShiftTask) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun deleteShiftTask(context: Context, task: ShiftTask) {
        viewModelScope.launch {
            cancelShiftTaskAlarm(context, task)
            repository.deleteTask(task)
        }
    }

    fun saveShiftHandover(
        stationId: String,
        stationName: String,
        shiftTitle: String,
        content: String,
        senderName: String
    ) {
        viewModelScope.launch {
            val handover = ShiftHandover(
                stationId = stationId,
                stationName = stationName,
                shiftTitle = shiftTitle,
                handoverContent = content,
                senderName = senderName,
                timestamp = System.currentTimeMillis()
            )
            repository.insertHandover(handover)
        }
    }

    fun deleteShiftHandover(handover: ShiftHandover) {
        viewModelScope.launch {
            repository.deleteHandover(handover)
        }
    }

    fun saveShiftStation(id: String? = null, name: String, icon: String, description: String, qrCode: String) {
        viewModelScope.launch {
            val stationId = id ?: java.util.UUID.randomUUID().toString()
            val station = ShiftStation(
                id = stationId,
                name = name,
                icon = icon,
                description = description,
                qrCode = if (qrCode.isBlank()) "STATION_${name.uppercase().replace(" ", "_")}" else qrCode
            )
            repository.insertStation(station)
            selectedStationId.value = stationId
            prefs?.edit()?.putString("selected_station_id", stationId)?.apply()
        }
    }

    fun deleteShiftStation(station: ShiftStation) {
        viewModelScope.launch {
            repository.deleteStationAndRelatedData(station)
            val remaining = repository.getAllShiftStationsDirect()
            if (selectedStationId.value == station.id) {
                val nextId = remaining.firstOrNull()?.id
                selectedStationId.value = nextId
                prefs?.edit()?.putString("selected_station_id", nextId ?: "")?.apply()
            }
            val newStarted = startedStationIds.value - station.id
            startedStationIds.value = newStarted
            isShiftStarted.value = newStarted.isNotEmpty()
            prefs?.edit()
                ?.putStringSet("started_station_ids", HashSet(newStarted))
                ?.putBoolean("is_shift_started", newStarted.isNotEmpty())
                ?.apply()
        }
    }

    suspend fun exportAllDataToZip(context: Context): java.io.File? {
        val cats = repository.getCategoriesDirect()
        val notes = repository.getAllNotesDirect()
        val quickNotes = repository.getAllQuickNotesDirect()
        val stations = repository.getAllShiftStationsDirect()
        val tasks = repository.getAllShiftTasksDirect()
        val handovers = repository.getAllShiftHandoversDirect()

        return com.example.utils.StorageUtils.exportDataToZip(
            context = context,
            categories = cats,
            notes = notes,
            quickNotes = quickNotes,
            shiftStations = stations,
            shiftTasks = tasks,
            shiftHandovers = handovers
        )
    }

    suspend fun selectStationByQrCode(qrCode: String, context: Context? = null): Boolean {
        val station = repository.getStationByQrCode(qrCode)
        return if (station != null) {
            selectStation(station.id, context)
            true
        } else {
            false
        }
    }

    // --- AI SCHEDULE PLANNER ---
    private val aiScheduleService = AiScheduleService()

    private val initialAiGreeting = AiChatMessage(
        isUser = false,
        text = "Xin chào! Tôi là Trợ lý AI Lập lịch trình thông minh. Bạn có thể trò chuyện với tôi để lên lịch cho 1 ngày học tập & vui chơi, chia nhỏ quy trình kho cá theo từng phút, hoặc lập danh mục công việc cho ca trực máy. Tôi sẽ tự động sắp xếp các mốc thời gian và bạn có thể chỉnh sửa tùy ý!",
        plan = null
    )

    val aiChatMessages = MutableStateFlow<List<AiChatMessage>>(listOf(initialAiGreeting))
    val isAiThinking = MutableStateFlow(false)

    fun sendAiSchedulePrompt(userPrompt: String) {
        val prompt = userPrompt.trim()
        if (prompt.isBlank()) return

        val userMessage = AiChatMessage(
            isUser = true,
            text = prompt
        )
        aiChatMessages.value = aiChatMessages.value + userMessage
        isAiThinking.value = true

        viewModelScope.launch {
            try {
                val resp = aiScheduleService.generateSchedule(prompt, aiChatMessages.value)
                val aiMessage = AiChatMessage(
                    isUser = false,
                    text = resp.text,
                    plan = resp.plan,
                    rotationPlan = resp.rotationPlan
                )
                aiChatMessages.value = aiChatMessages.value + aiMessage
            } catch (e: Exception) {
                val fallback = aiScheduleService.generateSmartLocalSchedule(prompt)
                aiChatMessages.value = aiChatMessages.value + AiChatMessage(
                    isUser = false,
                    text = fallback.text,
                    plan = fallback.plan,
                    rotationPlan = fallback.rotationPlan
                )
            } finally {
                isAiThinking.value = false
            }
        }
    }

    val shiftRotationConfig = MutableStateFlow<ShiftRotationConfig>(
        appContext?.let { ShiftRotationManager.loadConfig(it) }
            ?: ShiftRotationManager.createDefault2WeekConfig(System.currentTimeMillis())
    )

    fun reloadShiftRotationConfig(context: Context) {
        shiftRotationConfig.value = ShiftRotationManager.loadConfig(context)
    }

    fun saveShiftRotationConfig(context: Context, config: ShiftRotationConfig) {
        viewModelScope.launch {
            ShiftRotationManager.saveConfig(context, config)
            shiftRotationConfig.value = config
            if (config.isEnabled) {
                val result = ShiftRotationManager.applyRotationForToday(context)
                if (result.first != null) {
                    val newSet = setOf(result.first!!.id)
                    startedStationIds.value = newSet
                    selectedStationId.value = result.first!!.id
                    isShiftStarted.value = true
                    prefs?.edit()
                        ?.putStringSet("started_station_ids", HashSet(newSet))
                        ?.putBoolean("is_shift_started", true)
                        ?.putString("selected_station_id", result.first!!.id)
                        ?.apply()
                }
            }
        }
    }

    fun applyRotationPlanFromAi(context: Context, plan: AiRotationPlan) {
        viewModelScope.launch {
            val existingStations = repository.getAllShiftStationsDirect()
            val stationMap = mutableMapOf<String, String>() // name -> id

            // Ensure stations mentioned in the plan ("ca ngày", "ca đêm", "ngày nghỉ") exist in DB
            plan.weeks.flatMap { it.days }.map { it.stationName.trim() }.distinct().forEach { stName ->
                val match = existingStations.find { it.name.trim().equals(stName, ignoreCase = true) }
                if (match != null) {
                    stationMap[stName.lowercase()] = match.id
                } else {
                    val icon = when {
                        stName.contains("ngày", ignoreCase = true) -> "☀️"
                        stName.contains("đêm", ignoreCase = true) -> "🌙"
                        stName.contains("nghỉ", ignoreCase = true) -> "🌴"
                        else -> "🏭"
                    }
                    val newSt = ShiftStation(
                        name = stName,
                        icon = icon,
                        description = "Tự động tạo theo chu kỳ xoay ca"
                    )
                    repository.insertStation(newSt)
                    stationMap[stName.lowercase()] = newSt.id
                }
            }

            val currentMonday = ShiftRotationManager.getMondayOfDate(System.currentTimeMillis())
            val assignments = mutableListOf<ShiftDayAssignment>()

            plan.weeks.forEachIndexed { wIdx, week ->
                week.days.forEach { day ->
                    val stId = stationMap[day.stationName.trim().lowercase()]
                    assignments.add(
                        ShiftDayAssignment(
                            weekIndex = wIdx,
                            dayOfWeek = day.dayOfWeek,
                            stationId = stId,
                            targetStationName = day.stationName
                        )
                    )
                }
            }

            val newConfig = ShiftRotationConfig(
                isEnabled = true,
                cycleWeeks = plan.cycleWeeks,
                anchorMondayMillis = currentMonday,
                switchTimeStr = "06:00",
                notifyOnSwitch = true,
                assignments = assignments
            )

            ShiftRotationManager.saveConfig(context, newConfig)
            shiftRotationConfig.value = newConfig

            val applied = ShiftRotationManager.applyRotationForToday(context)
            if (applied.first != null) {
                val newSet = setOf(applied.first!!.id)
                startedStationIds.value = newSet
                selectedStationId.value = applied.first!!.id
                isShiftStarted.value = true
                prefs?.edit()
                    ?.putStringSet("started_station_ids", HashSet(newSet))
                    ?.putBoolean("is_shift_started", true)
                    ?.putString("selected_station_id", applied.first!!.id)
                    ?.apply()
            }

            android.widget.Toast.makeText(context, "🔄 Đã kích hoạt chu kỳ xoay ca: ${plan.title}!", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun clearAiChat() {
        aiChatMessages.value = listOf(initialAiGreeting)
    }

    fun generateExternalPrompt(topic: String, startTime: String = "07:30", endTime: String = "22:00"): String {
        return aiScheduleService.generateExternalPrompt(topic, startTime, endTime)
    }

    fun importExternalAiSchedule(rawText: String): Pair<Boolean, String> {
        val (plan, errorMsg) = aiScheduleService.parseExternalSchedule(rawText)
        if (plan != null) {
            val userMsg = AiChatMessage(
                isUser = true,
                text = "📥 [Đã nạp lịch trình từ AI ngoài: ${plan.title}]"
            )
            val aiMsg = AiChatMessage(
                isUser = false,
                text = "🎉 **Đã nhập thành công lịch trình từ AI ngoài!**\n\n📌 **Tiêu đề:** ${plan.title}\n📍 **Vị trí đề xuất:** ${plan.suggestedIcon} ${plan.suggestedStationName}\n⏱️ **Số lượng bước:** ${plan.steps.size} bước công việc.\n\nBạn có thể nhấn **'Bắt đầu BÂY GIỜ'** để tịnh tiến thời gian về hiện tại, nhấn vào từng bước để chỉnh sửa hoặc nhấn nút **'Tạo vị trí mới'** bên dưới.",
                plan = plan
            )
            aiChatMessages.value = aiChatMessages.value + userMsg + aiMsg
            return Pair(true, "Đã nhập thành công ${plan.steps.size} bước công việc!")
        } else {
            return Pair(false, errorMsg ?: "Không thể phân tích cú pháp lịch trình.")
        }
    }

    fun applyAiScheduleToStation(
        context: Context,
        stationId: String,
        steps: List<AiScheduleStep>,
        startShiftImmediately: Boolean = false
    ) {
        viewModelScope.launch {
            steps.forEach { step ->
                val parsed = parseHourMinute(step.timeScheduled)
                val normalizedTime = if (parsed != null) {
                    String.format(java.util.Locale.getDefault(), "%02d:%02d", parsed.first, parsed.second)
                } else {
                    step.timeScheduled.trim()
                }

                val task = ShiftTask(
                    id = java.util.UUID.randomUUID().toString(),
                    stationId = stationId,
                    timeScheduled = normalizedTime,
                    title = step.title,
                    instruction = step.instruction,
                    priority = step.priority,
                    requiresConfirmation = false,
                    isCompleted = false,
                    hasLoudAlarm = step.hasLoudAlarm
                )
                repository.insertTask(task)
            }
            selectStation(stationId, context)

            if (startShiftImmediately) {
                startShiftForStation(stationId, "Lịch trình AI", context)
            }
        }
    }

    fun createStationAndApplyAiSchedule(
        context: Context,
        stationName: String,
        stationIcon: String = "✨",
        steps: List<AiScheduleStep>,
        startShiftImmediately: Boolean = false
    ) {
        viewModelScope.launch {
            val newStationId = java.util.UUID.randomUUID().toString()
            val station = ShiftStation(
                id = newStationId,
                name = stationName.ifBlank { "Lịch trình AI" },
                icon = stationIcon.ifBlank { "✨" },
                description = "Lập bởi Trợ lý AI",
                qrCode = "STATION_${newStationId.take(8)}"
            )
            repository.insertStation(station)
            selectedStationId.value = newStationId
            prefs?.edit()?.putString("selected_station_id", newStationId)?.apply()

            steps.forEach { step ->
                val parsed = parseHourMinute(step.timeScheduled)
                val normalizedTime = if (parsed != null) {
                    String.format(java.util.Locale.getDefault(), "%02d:%02d", parsed.first, parsed.second)
                } else {
                    step.timeScheduled.trim()
                }

                val task = ShiftTask(
                    id = java.util.UUID.randomUUID().toString(),
                    stationId = newStationId,
                    timeScheduled = normalizedTime,
                    title = step.title,
                    instruction = step.instruction,
                    priority = step.priority,
                    requiresConfirmation = false,
                    isCompleted = false,
                    hasLoudAlarm = step.hasLoudAlarm
                )
                repository.insertTask(task)
            }

            if (startShiftImmediately) {
                startShiftForStation(newStationId, "Lịch trình AI", context)
            }
        }
    }

    fun toggleTaskLoudAlarm(task: ShiftTask, context: Context) {
        viewModelScope.launch {
            val updated = task.copy(hasLoudAlarm = !task.hasLoudAlarm)
            repository.updateTask(updated)
            if (startedStationIds.value.contains(task.stationId)) {
                cancelShiftTaskAlarm(context, task)
                if (!updated.isHidden && !updated.isCompleted) {
                    scheduleShiftTaskAlarm(context, updated)
                }
            }
        }
    }

    fun setStationAllTasksLoudAlarm(stationId: String, enableLoud: Boolean, context: Context) {
        viewModelScope.launch {
            val tasks = repository.getAllShiftTasksDirect().filter { it.stationId == stationId }
            tasks.forEach { task ->
                val updated = task.copy(hasLoudAlarm = enableLoud)
                repository.updateTask(updated)
                if (startedStationIds.value.contains(stationId)) {
                    cancelShiftTaskAlarm(context, task)
                    if (!updated.isHidden && !updated.isCompleted) {
                        scheduleShiftTaskAlarm(context, updated)
                    }
                }
            }
        }
    }

    fun rescheduleStationTasks(
        context: Context,
        stationId: String,
        newStartTimeStr: String,
        resetCompleted: Boolean = true
    ) {
        viewModelScope.launch {
            val tasks = repository.getTasksForStationDirect(stationId)
            if (tasks.isEmpty()) return@launch

            val parsedNewStart = parseHourMinute(newStartTimeStr) ?: return@launch
            val newStartMinutes = parsedNewStart.first * 60 + parsedNewStart.second

            // Sort tasks by current scheduled time
            val sortedTasks = tasks.sortedBy { task ->
                parseHourMinute(task.timeScheduled)?.let { it.first * 60 + it.second } ?: 0
            }

            val firstTaskMinutes = parseHourMinute(sortedTasks.first().timeScheduled)?.let {
                it.first * 60 + it.second
            } ?: newStartMinutes

            sortedTasks.forEach { task ->
                // Cancel existing alarm
                cancelShiftTaskAlarm(context, task)

                val taskMinutes = parseHourMinute(task.timeScheduled)?.let {
                    it.first * 60 + it.second
                } ?: firstTaskMinutes

                val diffFromFirst = (taskMinutes - firstTaskMinutes).let {
                    if (it < 0) it + 1440 else it
                }

                val targetMinutes = (newStartMinutes + diffFromFirst) % 1440
                val newHour = targetMinutes / 60
                val newMin = targetMinutes % 60
                val newTimeScheduled = String.format(java.util.Locale.getDefault(), "%02d:%02d", newHour, newMin)

                val updatedTask = task.copy(
                    timeScheduled = newTimeScheduled,
                    isCompleted = if (resetCompleted) false else task.isCompleted,
                    completedAt = if (resetCompleted) null else task.completedAt
                )
                repository.updateTask(updatedTask)

                if (startedStationIds.value.contains(stationId) && !updatedTask.isHidden && !updatedTask.isCompleted) {
                    scheduleShiftTaskAlarm(context, updatedTask)
                }
            }
        }
    }

    fun shiftAiPlanSteps(steps: List<AiScheduleStep>, newStartTimeStr: String): List<AiScheduleStep> {
        if (steps.isEmpty()) return steps
        val parsedNewStart = parseHourMinute(newStartTimeStr) ?: return steps
        val newStartMinutes = parsedNewStart.first * 60 + parsedNewStart.second

        val firstMinutes = parseHourMinute(steps.first().timeScheduled)?.let {
            it.first * 60 + it.second
        } ?: newStartMinutes

        return steps.map { step ->
            val curMinutes = parseHourMinute(step.timeScheduled)?.let {
                it.first * 60 + it.second
            } ?: firstMinutes
            val diff = (curMinutes - firstMinutes).let { if (it < 0) it + 1440 else it }
            val targetMin = (newStartMinutes + diff) % 1440
            val h = targetMin / 60
            val m = targetMin % 60
            step.copy(timeScheduled = String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m))
        }
    }

    fun getInstalledApps(context: Context): List<InstalledAppItem> {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            apps.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                val name = resolveInfo.loadLabel(pm)?.toString() ?: pkg
                InstalledAppItem(packageName = pkg, appName = name)
            }.distinctBy { it.packageName }
             .sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager ?: return false
        val comp = android.content.ComponentName(context, AppDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(comp)
    }

    fun getDeviceAdminPromptIntent(context: Context): Intent {
        val comp = android.content.ComponentName(context, AppDeviceAdminReceiver::class.java)
        return Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
            putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Cho phép ứng dụng tự động khóa màn hình và đóng ứng dụng khi đến giờ hẹn đi ngủ.")
        }
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun getOverlayPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent()
        }
    }

    fun isNotificationPolicyAccessGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.isNotificationPolicyAccessGranted ?: true
        } else {
            true
        }
    }

    fun getNotificationPolicyAccessIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        } else {
            Intent()
        }
    }

    // =========================================================================
    // --- ROADMAP & MULTI-MONTH AI PLANNING (LỘ TRÌNH & KẾ HOẠCH ĐA THÁNG) ---
    // =========================================================================

    val allRoadmaps: StateFlow<List<Roadmap>> = repository.allRoadmaps
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedRoadmapId = MutableStateFlow<String?>(null)

    val selectedRoadmap: StateFlow<Roadmap?> = combine(allRoadmaps, selectedRoadmapId) { roadmaps, id ->
        if (id != null) roadmaps.find { it.id == id } else roadmaps.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedRoadmapTasks: StateFlow<List<RoadmapTask>> = selectedRoadmap
        .flatMapLatest { roadmap ->
            if (roadmap == null) flowOf(emptyList()) else repository.getTasksForRoadmap(roadmap.id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val initialRoadmapGreeting = AiRoadmapChatMessage(
        isUser = false,
        text = "Xin chào! Tôi là Trợ lý AI Lập lộ trình & Kế hoạch Đa tháng. Bạn có thể trò chuyện với tôi để lên lịch trình chi tiết trải dài nhiều tháng: từ kế hoạch nông nghiệp (trồng ngô, trồng lúa 3 tháng, chăm sóc hoa màu theo đợt bón phân/phun thuốc), đến hành trình du lịch phượt 1 tháng xuyên Việt, ôn thi, rèn luyện thể chất hay xây dựng dự án. Tôi sẽ tự động tính toán các mốc ngày và hẹn giờ thông báo cho bạn!",
        roadmapPlan = null
    )

    val aiRoadmapChatMessages = MutableStateFlow<List<AiRoadmapChatMessage>>(listOf(initialRoadmapGreeting))
    val isAiRoadmapThinking = MutableStateFlow(false)

    fun selectRoadmap(id: String?) {
        selectedRoadmapId.value = id
    }

    fun sendRoadmapChatMessage(userPrompt: String, startDateStr: String) {
        val prompt = userPrompt.trim()
        if (prompt.isBlank()) return

        val userMessage = AiRoadmapChatMessage(
            isUser = true,
            text = prompt
        )
        aiRoadmapChatMessages.value = aiRoadmapChatMessages.value + userMessage
        isAiRoadmapThinking.value = true

        viewModelScope.launch {
            try {
                val (replyText, plan) = aiScheduleService.generateRoadmapPlan(
                    userPrompt = prompt,
                    startDateStr = startDateStr,
                    history = aiRoadmapChatMessages.value
                )
                val aiMessage = AiRoadmapChatMessage(
                    isUser = false,
                    text = replyText,
                    roadmapPlan = plan
                )
                aiRoadmapChatMessages.value = aiRoadmapChatMessages.value + aiMessage
            } catch (e: Exception) {
                e.printStackTrace()
                aiRoadmapChatMessages.value = aiRoadmapChatMessages.value + AiRoadmapChatMessage(
                    isUser = false,
                    text = "Không thể kết nối AI, vui lòng thử lại: ${e.localizedMessage ?: "Lỗi mạng"}"
                )
            } finally {
                isAiRoadmapThinking.value = false
            }
        }
    }

    fun clearRoadmapChat() {
        aiRoadmapChatMessages.value = listOf(initialRoadmapGreeting)
    }

    fun generateExternalRoadmapPrompt(
        topic: String,
        startDate: String = "2026-10-21",
        durationDays: Int = 30,
        category: String = "Nông nghiệp"
    ): String {
        return aiScheduleService.generateExternalRoadmapPrompt(topic, startDate, durationDays, category)
    }

    fun importExternalAiRoadmap(rawText: String): Pair<Boolean, String> {
        val (plan, errorMsg) = aiScheduleService.parseExternalRoadmap(rawText)
        if (plan != null) {
            val userMsg = AiRoadmapChatMessage(
                isUser = true,
                text = "📥 [Đã nạp lộ trình từ AI ngoài: ${plan.title}]"
            )
            val aiMsg = AiRoadmapChatMessage(
                isUser = false,
                text = "🎉 **Đã nhập thành công lộ trình từ AI ngoài!**\n\n📌 **Lộ trình:** ${plan.icon} ${plan.title}\n📂 **Danh mục:** ${plan.category}\n📅 **Thời lượng:** ${plan.totalDays} ngày (${plan.tasks.size} mốc công việc)\n📝 **Mô tả:** ${plan.description}\n\nBạn có thể kiểm tra danh sách các mốc bên dưới và nhấn **'Áp dụng và lưu lộ trình'** để bắt đầu theo dõi.",
                roadmapPlan = plan
            )
            aiRoadmapChatMessages.value = aiRoadmapChatMessages.value + userMsg + aiMsg
            return Pair(true, "Đã nhập thành công lộ trình '${plan.title}' với ${plan.tasks.size} mốc công việc!")
        } else {
            return Pair(false, errorMsg ?: "Không thể phân tích cú pháp lộ trình.")
        }
    }

    fun applyAndSaveRoadmap(plan: AiRoadmapPlan, startDateStr: String, context: Context) {
        viewModelScope.launch {
            try {
                val startCal = java.util.Calendar.getInstance()
                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                try {
                    val parsed = sdfDate.parse(startDateStr)
                    if (parsed != null) startCal.time = parsed
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val roadmap = Roadmap(
                    title = plan.title,
                    category = plan.category,
                    icon = plan.icon,
                    startDate = startDateStr,
                    totalDays = plan.totalDays,
                    description = plan.description
                )
                repository.insertRoadmap(roadmap)

                val tasks = plan.tasks.map { step ->
                    val taskCal = startCal.clone() as java.util.Calendar
                    taskCal.add(java.util.Calendar.DAY_OF_YEAR, step.dayOffset)
                    val dateFormatted = sdfDate.format(taskCal.time)

                    RoadmapTask(
                        roadmapId = roadmap.id,
                        dayOffset = step.dayOffset,
                        scheduledDate = dateFormatted,
                        scheduledTime = step.scheduledTime,
                        title = step.title,
                        instruction = step.instruction,
                        phase = step.phase,
                        priority = step.priority,
                        hasReminder = true,
                        hasLoudAlarm = step.hasLoudAlarm
                    )
                }

                repository.insertRoadmapTasks(tasks)
                selectedRoadmapId.value = roadmap.id

                // Schedule notification alarms for all upcoming tasks
                for (t in tasks) {
                    scheduleRoadmapTaskAlarm(context, t)
                }

                android.widget.Toast.makeText(
                    context,
                    "🎉 Đã lưu lộ trình '${roadmap.title}' (${tasks.size} công việc) và kích hoạt nhắc nhở!",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Lỗi khi lưu lộ trình: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleRoadmapTaskCompleted(task: RoadmapTask, context: Context) {
        viewModelScope.launch {
            val isNowCompleted = !task.isCompleted
            val updated = task.copy(
                isCompleted = isNowCompleted,
                completedAt = if (isNowCompleted) System.currentTimeMillis() else null
            )
            repository.updateRoadmapTask(updated)
            if (isNowCompleted) {
                cancelRoadmapTaskAlarm(context, task)
            } else {
                scheduleRoadmapTaskAlarm(context, updated)
            }
        }
    }

    fun updateRoadmapTask(task: RoadmapTask, context: Context) {
        viewModelScope.launch {
            repository.updateRoadmapTask(task)
            if (task.isCompleted || !task.hasReminder) {
                cancelRoadmapTaskAlarm(context, task)
            } else {
                scheduleRoadmapTaskAlarm(context, task)
            }
        }
    }

    fun deleteRoadmapTask(task: RoadmapTask, context: Context) {
        viewModelScope.launch {
            cancelRoadmapTaskAlarm(context, task)
            repository.deleteRoadmapTask(task)
        }
    }

    fun addCustomRoadmapTask(task: RoadmapTask, context: Context) {
        viewModelScope.launch {
            repository.insertRoadmapTask(task)
            if (task.hasReminder && !task.isCompleted) {
                scheduleRoadmapTaskAlarm(context, task)
            }
        }
    }

    fun updateRoadmap(roadmap: Roadmap, context: Context) {
        viewModelScope.launch {
            repository.updateRoadmap(roadmap)
            android.widget.Toast.makeText(context, "Đã cập nhật thông tin lộ trình", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteRoadmap(roadmap: Roadmap, context: Context) {
        viewModelScope.launch {
            val tasks = repository.getAllRoadmapTasksDirect().filter { it.roadmapId == roadmap.id }
            for (t in tasks) {
                cancelRoadmapTaskAlarm(context, t)
            }
            repository.deleteRoadmap(roadmap)
            if (selectedRoadmapId.value == roadmap.id) {
                selectedRoadmapId.value = null
            }
            android.widget.Toast.makeText(context, "Đã xóa lộ trình '${roadmap.title}'", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun scheduleRoadmapTaskAlarm(context: Context, task: RoadmapTask) {
        if (task.isCompleted || !task.hasReminder) return

        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = sdf.parse("${task.scheduledDate} ${task.scheduledTime}") ?: return
            val triggerTimeMs = date.time
            val now = System.currentTimeMillis()

            // If time is more than 30s in the past, do not ring
            if (triggerTimeMs < now - 30_000) return

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("NOTE_ID", task.id)
                putExtra("NOTE_TEXT", "${task.title}\n${task.instruction}")
                putExtra("CATEGORY_NAME", "${task.phase} (${task.scheduledTime})")
                putExtra("IS_ROADMAP_TASK", true)
                putExtra("ROADMAP_ID", task.roadmapId)
                putExtra("HAS_LOUD_ALARM", task.hasLoudAlarm)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val showIntent = Intent(context, MainActivity::class.java)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                task.id.hashCode(),
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelRoadmapTaskAlarm(context: Context, task: RoadmapTask) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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
}

data class InstalledAppItem(
    val packageName: String,
    val appName: String
)

