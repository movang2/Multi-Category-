package com.example

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.QuickNote
import com.example.utils.QuickNoteAlarmHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class QuickNoteWidgetActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "extra_tab"
        const val EXTRA_EDIT_NOTE_ID = "extra_edit_note_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialTab = intent.getIntExtra(EXTRA_TAB, 0)
        val initialNoteId = intent.getStringExtra(EXTRA_EDIT_NOTE_ID)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF64B5F6),
                    onPrimary = Color(0xFF0D47A1),
                    surface = Color(0xFF1E232A),
                    surfaceVariant = Color(0xFF28303B),
                    onSurface = Color(0xFFECEFF1),
                    onSurfaceVariant = Color(0xFFB0BEC5),
                    background = Color(0x99000000),
                    secondary = Color(0xFF4DD0E1),
                    outline = Color(0xFF455A64)
                )
            ) {
                QuickNoteWidgetScreen(
                    initialTab = initialTab,
                    initialNoteId = initialNoteId,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteWidgetScreen(
    initialTab: Int,
    initialNoteId: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(initialTab) }
    var notesList by remember { mutableStateOf<List<QuickNote>>(emptyList()) }
    var activeNoteToEdit by remember { mutableStateOf<QuickNote?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Load and observe notes from Room Database
    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        db.quickNoteDao().getAllQuickNotesFlow().collectLatest { notes ->
            notesList = notes
            if (initialNoteId != null && activeNoteToEdit == null && !showEditDialog) {
                val match = notes.find { it.id == initialNoteId }
                if (match != null) {
                    activeNoteToEdit = match
                    showEditDialog = true
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .heightIn(max = 680.dp)
                .clickable(enabled = false) {}, // Prevent clicks from dismissing dialog
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚡", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Ghi chú nhanh Widget",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tạo & quản lý giờ thông báo không cần mở app",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Privacy Indicator & Toggle for Widget
                val prefs = remember { context.getSharedPreferences("notes_app_prefs", android.content.Context.MODE_PRIVATE) }
                var isPrivacyEnabled by remember { mutableStateOf(prefs.getBoolean("hide_widget_note_content", true)) }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isPrivacyEnabled) Color(0xFF064E3B).copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (isPrivacyEnabled) Color(0xFF10B981).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newVal = !isPrivacyEnabled
                            isPrivacyEnabled = newVal
                            prefs.edit().putBoolean("hide_widget_note_content", newVal).apply()
                            QuickNoteAlarmHelper.updateWidget(context)
                            Toast.makeText(
                                context,
                                if (newVal) "🔒 Đã bật bảo mật: Ẩn nội dung trên Widget màn hình chính" else "👁️ Đã tắt bảo mật: Hiện nội dung trên Widget",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (isPrivacyEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isPrivacyEnabled) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPrivacyEnabled) "🔒 Bảo mật: Ẩn nội dung trên Widget màn hình chính" else "👁️ Đang hiện nội dung trên Widget",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isPrivacyEnabled) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Switch(
                            checked = isPrivacyEnabled,
                            onCheckedChange = { newVal ->
                                isPrivacyEnabled = newVal
                                prefs.edit().putBoolean("hide_widget_note_content", newVal).apply()
                                QuickNoteAlarmHelper.updateWidget(context)
                                Toast.makeText(
                                    context,
                                    if (newVal) "🔒 Đã bật bảo mật: Ẩn nội dung trên Widget màn hình chính" else "👁️ Đã tắt bảo mật",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.scale(0.75f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("✍️ Ghi mới", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📋 Danh sách (${notesList.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content View according to selected tab
                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        WidgetNewNoteView(
                            onSaveSuccess = {
                                onDismiss()
                            }
                        )
                    } else {
                        WidgetNotesListView(
                            notes = notesList,
                            onEditNote = { note ->
                                activeNoteToEdit = note
                                showEditDialog = true
                            },
                            onFastRearm = { note, mins ->
                                coroutineScope.launch {
                                    QuickNoteAlarmHelper.rearmAlarm(context, note, mins)
                                    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    val newTime = System.currentTimeMillis() + mins * 60 * 1000L
                                    Toast.makeText(
                                        context,
                                        "🔔 Đã hẹn lại báo lúc ${timeFmt.format(Date(newTime))} (sau $mins phút)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onCancelAlarm = { note ->
                                coroutineScope.launch {
                                    val updated = note.copy(
                                        hasNotification = false,
                                        reminderTime = null,
                                        isNotificationTriggered = false,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    QuickNoteAlarmHelper.saveOrUpdateNote(context, updated)
                                    Toast.makeText(context, "Đã tắt thông báo của ghi chú này", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDeleteNote = { note ->
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        QuickNoteAlarmHelper.cancelAlarm(context, note.id)
                                        AppDatabase.getDatabase(context).quickNoteDao().deleteQuickNote(note)
                                        QuickNoteAlarmHelper.updateWidget(context)
                                    }
                                    Toast.makeText(context, "Đã xóa ghi chú", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal Dialog to edit existing note or its reminder
    if (showEditDialog && activeNoteToEdit != null) {
        WidgetEditNoteReminderDialog(
            note = activeNoteToEdit!!,
            onDismiss = {
                showEditDialog = false
                activeNoteToEdit = null
            },
            onSave = { updatedNote ->
                coroutineScope.launch {
                    QuickNoteAlarmHelper.saveOrUpdateNote(context, updatedNote, activeNoteToEdit?.id)
                    showEditDialog = false
                    activeNoteToEdit = null
                    if (updatedNote.hasNotification && updatedNote.reminderTime != null) {
                        val sdf = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
                        Toast.makeText(
                            context,
                            "⏰ Đã cập nhật giờ báo: ${sdf.format(Date(updatedNote.reminderTime))}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(context, "Đã lưu cập nhật ghi chú", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

/**
 * Tab 0: Create new quick note with full notification scheduling options
 */
@Composable
fun WidgetNewNoteView(
    onSaveSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var noteText by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }

    // Notification states
    var hasNotification by remember { mutableStateOf(false) }
    var notificationMode by remember { mutableStateOf(0) } // 0 = Sau X phút, 1 = Giờ cụ thể
    var minutesOffsetStr by remember { mutableStateOf("2") } // Mặc định 2 phút

    val nowCal = remember { Calendar.getInstance() }
    var selectedHour by remember { mutableStateOf((nowCal.get(Calendar.HOUR_OF_DAY) + 1) % 24) }
    var selectedMinute by remember { mutableStateOf(0) }
    var selectedDateType by remember { mutableStateOf(0) } // 0 = Hôm nay, 1 = Ngày mai, 2 = Ngày khác
    var customDateCalendar by remember { mutableStateOf(Calendar.getInstance()) }

    // Calculated target trigger timestamp
    val calculatedReminderTimeMs: Long? = remember(
        hasNotification,
        notificationMode,
        minutesOffsetStr,
        selectedHour,
        selectedMinute,
        selectedDateType,
        customDateCalendar.timeInMillis
    ) {
        if (!hasNotification) return@remember null

        if (notificationMode == 0) {
            val mins = (minutesOffsetStr.trim().toIntOrNull() ?: 2).coerceAtLeast(1)
            System.currentTimeMillis() + (mins * 60 * 1000L)
        } else {
            val cal = Calendar.getInstance().apply {
                when (selectedDateType) {
                    0 -> {} // Hôm nay
                    1 -> add(Calendar.DAY_OF_YEAR, 1) // Ngày mai
                    2 -> timeInMillis = customDateCalendar.timeInMillis // Ngày khác
                }
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (selectedDateType == 0 && cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            cal.timeInMillis
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            placeholder = { Text("Viết nội dung ghi chú nhanh...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = pinned,
                onCheckedChange = { pinned = it },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Ghim lên đầu", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Notification Settings Section
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (hasNotification) Color(0xFF0D3328) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(
                1.dp,
                if (hasNotification) Color(0xFF10B981).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { hasNotification = !hasNotification },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasNotification) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = if (hasNotification) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Phát thông báo nhắc nhở",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (hasNotification) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (hasNotification) "Báo thức sẽ nhắc khi đến giờ" else "Không đặt thông báo (lưu bình thường)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = hasNotification,
                        onCheckedChange = { hasNotification = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF10B981),
                            checkedTrackColor = Color(0xFF064E3B)
                        )
                    )
                }

                AnimatedVisibility(visible = hasNotification) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Mode Selector: Countdown vs Specific Time
                        Row(modifier = Modifier.fillMaxWidth()) {
                            FilterChip(
                                selected = notificationMode == 0,
                                onClick = { notificationMode = 0 },
                                label = { Text("⏱️ Sau X phút", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = notificationMode == 1,
                                onClick = { notificationMode = 1 },
                                label = { Text("🕒 Giờ cụ thể", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (notificationMode == 0) {
                            // Quick minute offsets
                            Text("Chọn nhanh hoặc nhập số phút:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))

                            val presets = listOf(2, 5, 10, 15, 30, 60, 120)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presets.forEach { min ->
                                    val isSelected = minutesOffsetStr == min.toString()
                                    val label = if (min >= 60) "+${min / 60}h" else "+${min}p"
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                        modifier = Modifier.clickable { minutesOffsetStr = min.toString() }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = minutesOffsetStr,
                                onValueChange = { newVal ->
                                    if (newVal.all { it.isDigit() } && newVal.length <= 4) {
                                        minutesOffsetStr = newVal
                                    }
                                },
                                label = { Text("Số phút sau sẽ phát thông báo:", fontSize = 11.sp) },
                                placeholder = { Text("2") },
                                trailingIcon = { Text("phút", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        } else {
                            // Specific Time Picker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Giờ thông báo:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                OutlinedButton(
                                    onClick = {
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                selectedHour = hourOfDay
                                                selectedMinute = minute
                                            },
                                            selectedHour,
                                            selectedMinute,
                                            true
                                        ).show()
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Hôm nay", "Ngày mai", "Ngày khác...").forEachIndexed { idx, title ->
                                    FilterChip(
                                        selected = selectedDateType == idx,
                                        onClick = {
                                            selectedDateType = idx
                                            if (idx == 2) {
                                                val now = Calendar.getInstance()
                                                DatePickerDialog(
                                                    context,
                                                    { _, year, month, dayOfMonth ->
                                                        customDateCalendar = Calendar.getInstance().apply {
                                                            set(year, month, dayOfMonth)
                                                        }
                                                    },
                                                    now.get(Calendar.YEAR),
                                                    now.get(Calendar.MONTH),
                                                    now.get(Calendar.DAY_OF_MONTH)
                                                ).show()
                                            }
                                        },
                                        label = { Text(title, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Live Preview Banner
                        calculatedReminderTimeMs?.let { timeMs ->
                            Spacer(modifier = Modifier.height(10.dp))
                            val sdf = SimpleDateFormat("HH:mm - EEEE, dd/MM", Locale("vi"))
                            val remainingMins = ((timeMs - System.currentTimeMillis()) / (60 * 1000L)).coerceAtLeast(0)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF042F2E),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Nhắc lúc ${sdf.format(Date(timeMs))} (sau $remainingMins phút)",
                                        fontSize = 11.sp,
                                        color = Color(0xFF2DD4BF),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val textToSave = noteText.trim()
                    if (textToSave.isEmpty()) {
                        Toast.makeText(context, "Vui lòng nhập nội dung ghi chú!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    coroutineScope.launch {
                        val validReminder = hasNotification && calculatedReminderTimeMs != null
                        val newNote = QuickNote(
                            text = textToSave,
                            pinned = pinned,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            hasNotification = validReminder,
                            reminderTime = if (validReminder) calculatedReminderTimeMs else null,
                            isNotificationTriggered = false
                        )
                        QuickNoteAlarmHelper.saveOrUpdateNote(context, newNote)
                        if (validReminder && calculatedReminderTimeMs != null) {
                            val timeFmt = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
                            Toast.makeText(
                                context,
                                "⏰ Đã lưu & hẹn giờ nhắc lúc ${timeFmt.format(Date(calculatedReminderTimeMs))}",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(context, "Đã lưu ghi chú nhanh", Toast.LENGTH_SHORT).show()
                        }
                        onSaveSuccess()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(if (hasNotification) Icons.Default.AlarmOn else Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (hasNotification) "Lưu & Bật thông báo" else "Lưu ghi chú", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Tab 1: List and direct reminder adjustment for all saved quick notes
 */
@Composable
fun WidgetNotesListView(
    notes: List<QuickNote>,
    onEditNote: (QuickNote) -> Unit,
    onFastRearm: (QuickNote, Int) -> Unit,
    onCancelAlarm: (QuickNote) -> Unit,
    onDeleteNote: (QuickNote) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(0) } // 0: Tất cả, 1: Đang hẹn giờ, 2: Đã báo

    val now = System.currentTimeMillis()

    val filteredNotes = remember(notes, searchQuery, filterMode) {
        notes.filter { note ->
            val matchQuery = searchQuery.isBlank() || note.text.contains(searchQuery, ignoreCase = true)
            val matchFilter = when (filterMode) {
                1 -> note.hasNotification && note.reminderTime != null && note.reminderTime > now && !note.isNotificationTriggered
                2 -> note.hasNotification && (note.isNotificationTriggered || (note.reminderTime != null && note.reminderTime <= now))
                else -> true
            }
            matchQuery && matchFilter
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Filter Row
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Tìm kiếm ghi chú...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = filterMode == 0,
                onClick = { filterMode = 0 },
                label = { Text("Tất cả (${notes.size})", fontSize = 11.sp) }
            )
            val activeCount = notes.count { it.hasNotification && it.reminderTime != null && it.reminderTime > now && !it.isNotificationTriggered }
            FilterChip(
                selected = filterMode == 1,
                onClick = { filterMode = 1 },
                label = { Text("🔔 Đang hẹn ($activeCount)", fontSize = 11.sp) }
            )
            val triggeredCount = notes.count { it.hasNotification && (it.isNotificationTriggered || (it.reminderTime != null && it.reminderTime <= now)) }
            FilterChip(
                selected = filterMode == 2,
                onClick = { filterMode = 2 },
                label = { Text("✅ Đã báo ($triggeredCount)", fontSize = 11.sp) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.NoteAlt,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Không tìm thấy ghi chú phù hợp" else "Chưa có ghi chú nào",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredNotes, key = { it.id }) { note ->
                    WidgetNoteItemCard(
                        note = note,
                        onEdit = { onEditNote(note) },
                        onFastRearm = { mins -> onFastRearm(note, mins) },
                        onCancelAlarm = { onCancelAlarm(note) },
                        onDelete = { onDeleteNote(note) }
                    )
                }
            }
        }
    }
}

/**
 * Card for each quick note in widget list with quick re-arm and edit reminder affordances
 */
@Composable
fun WidgetNoteItemCard(
    note: QuickNote,
    onEdit: () -> Unit,
    onFastRearm: (Int) -> Unit,
    onCancelAlarm: () -> Unit,
    onDelete: () -> Unit
) {
    val now = System.currentTimeMillis()
    val isUpcoming = note.hasNotification && note.reminderTime != null && note.reminderTime > now && !note.isNotificationTriggered
    val isTriggered = note.hasNotification && (note.isNotificationTriggered || (note.reminderTime != null && note.reminderTime <= now))
    val timeFmt = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (isUpcoming) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Note Top row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.pinned) {
                        Text("📌", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = SimpleDateFormat("HH:mm, dd/MM", Locale.getDefault()).format(Date(note.createdAt)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Sửa", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Note Text Preview
            Text(
                text = note.text,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Reminder status row
            if (isUpcoming) {
                val remainingMins = ((note.reminderTime!! - now) / (60 * 1000L)).coerceAtLeast(0)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF042F2E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Alarm, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Báo lúc ${timeFmt.format(Date(note.reminderTime))} (sau ${remainingMins}p)",
                                fontSize = 11.sp,
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(
                            onClick = onCancelAlarm,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Tắt báo", fontSize = 11.sp, color = Color(0xFFF87171))
                        }
                    }
                }
            } else if (isTriggered) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⏰ Đã báo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "+2p",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .clickable { onFastRearm(2) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Text(
                                "+5p",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .clickable { onFastRearm(5) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Text(
                                "Chỉnh giờ",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .clickable { onEdit() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            } else {
                // No reminder set yet
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chưa cài thông báo", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "+2p nhắc",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .clickable { onFastRearm(2) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Text(
                            "+ Hẹn giờ",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .clickable { onEdit() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Xác nhận xóa", fontSize = 16.sp) },
            text = { Text("Bạn có chắc chắn muốn xóa ghi chú nhanh này?", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Xóa", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

/**
 * Dialog to edit existing note text & reminder directly from widget without opening main app
 */
@Composable
fun WidgetEditNoteReminderDialog(
    note: QuickNote,
    onDismiss: () -> Unit,
    onSave: (QuickNote) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(note.text) }
    var pinned by remember { mutableStateOf(note.pinned) }

    // Notification states
    var hasNotification by remember { mutableStateOf(note.hasNotification) }
    var notificationMode by remember { mutableStateOf(0) } // 0 = Sau X phút, 1 = Giờ cụ thể
    var minutesOffsetStr by remember { mutableStateOf("2") }

    val nowCal = remember { Calendar.getInstance() }
    var selectedHour by remember {
        val h = if (note.reminderTime != null) {
            val c = Calendar.getInstance().apply { timeInMillis = note.reminderTime }
            c.get(Calendar.HOUR_OF_DAY)
        } else {
            (nowCal.get(Calendar.HOUR_OF_DAY) + 1) % 24
        }
        mutableStateOf(h)
    }
    var selectedMinute by remember {
        val m = if (note.reminderTime != null) {
            val c = Calendar.getInstance().apply { timeInMillis = note.reminderTime }
            c.get(Calendar.MINUTE)
        } else {
            0
        }
        mutableStateOf(m)
    }
    var selectedDateType by remember { mutableStateOf(0) }
    var customDateCalendar by remember {
        val c = Calendar.getInstance()
        if (note.reminderTime != null) {
            c.timeInMillis = note.reminderTime
        }
        mutableStateOf(c)
    }

    val calculatedReminderTimeMs: Long? = remember(
        hasNotification,
        notificationMode,
        minutesOffsetStr,
        selectedHour,
        selectedMinute,
        selectedDateType,
        customDateCalendar.timeInMillis
    ) {
        if (!hasNotification) return@remember null

        if (notificationMode == 0) {
            val mins = (minutesOffsetStr.trim().toIntOrNull() ?: 2).coerceAtLeast(1)
            System.currentTimeMillis() + (mins * 60 * 1000L)
        } else {
            val cal = Calendar.getInstance().apply {
                when (selectedDateType) {
                    0 -> {}
                    1 -> add(Calendar.DAY_OF_YEAR, 1)
                    2 -> timeInMillis = customDateCalendar.timeInMillis
                }
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (selectedDateType == 0 && cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            cal.timeInMillis
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chỉnh sửa ghi chú & Giờ báo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ghim lên đầu", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Notification options
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasNotification) Color(0xFF0D3328) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, if (hasNotification) Color(0xFF10B981).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { hasNotification = !hasNotification },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🔔 Phát thông báo nhắc nhở",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (hasNotification) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = hasNotification,
                                onCheckedChange = { hasNotification = it }
                            )
                        }

                        AnimatedVisibility(visible = hasNotification) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    FilterChip(
                                        selected = notificationMode == 0,
                                        onClick = { notificationMode = 0 },
                                        label = { Text("Sau X phút", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    FilterChip(
                                        selected = notificationMode == 1,
                                        onClick = { notificationMode = 1 },
                                        label = { Text("Giờ cụ thể", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                if (notificationMode == 0) {
                                    val presets = listOf(2, 5, 10, 15, 30, 60)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        presets.forEach { min ->
                                            val isSel = minutesOffsetStr == min.toString()
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.clickable { minutesOffsetStr = min.toString() }
                                            ) {
                                                Text(
                                                    text = "+${min}p",
                                                    fontSize = 11.sp,
                                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    OutlinedTextField(
                                        value = minutesOffsetStr,
                                        onValueChange = { if (it.all { ch -> ch.isDigit() }) minutesOffsetStr = it },
                                        label = { Text("Số phút sau:", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Chọn giờ:", fontSize = 12.sp)
                                        OutlinedButton(
                                            onClick = {
                                                TimePickerDialog(
                                                    context,
                                                    { _, h, m -> selectedHour = h; selectedMinute = m },
                                                    selectedHour,
                                                    selectedMinute,
                                                    true
                                                ).show()
                                            }
                                        ) {
                                            Text(String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute), fontSize = 13.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("Hôm nay", "Ngày mai", "Ngày khác").forEachIndexed { idx, title ->
                                            FilterChip(
                                                selected = selectedDateType == idx,
                                                onClick = {
                                                    selectedDateType = idx
                                                    if (idx == 2) {
                                                        val c = Calendar.getInstance()
                                                        DatePickerDialog(
                                                            context,
                                                            { _, y, m, d -> customDateCalendar = Calendar.getInstance().apply { set(y, m, d) } },
                                                            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
                                                        ).show()
                                                    }
                                                },
                                                label = { Text(title, fontSize = 10.sp) }
                                            )
                                        }
                                    }
                                }

                                calculatedReminderTimeMs?.let { timeMs ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val sdf = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
                                    Text(
                                        text = "⏰ Sẽ nhắc lúc: ${sdf.format(Date(timeMs))}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF2DD4BF),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = text.trim()
                            if (trimmed.isEmpty()) {
                                Toast.makeText(context, "Nội dung trống!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val valid = hasNotification && calculatedReminderTimeMs != null
                            val updated = note.copy(
                                text = trimmed,
                                pinned = pinned,
                                updatedAt = System.currentTimeMillis(),
                                hasNotification = valid,
                                reminderTime = if (valid) calculatedReminderTimeMs else null,
                                isNotificationTriggered = false
                            )
                            onSave(updated)
                        }
                    ) {
                        Text("Cập nhật", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
