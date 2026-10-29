package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Roadmap
import com.example.data.RoadmapTask
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapManagementView(
    viewModel: NotesViewModel,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val roadmaps by viewModel.allRoadmaps.collectAsStateWithLifecycle()
    val selectedRoadmap by viewModel.selectedRoadmap.collectAsStateWithLifecycle()
    val tasks by viewModel.selectedRoadmapTasks.collectAsStateWithLifecycle()

    var showAiPlannerDialog by remember { mutableStateOf(false) }
    var initialAiPromptForDialog by remember { mutableStateOf<String?>(null) }
    var showEditRoadmapDialog by remember { mutableStateOf(false) }
    var roadmapToEdit by remember { mutableStateOf<Roadmap?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Roadmap?>(null) }

    var showTaskDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<RoadmapTask?>(null) }
    var taskToDeleteConfirm by remember { mutableStateOf<RoadmapTask?>(null) }

    // Filter state: "ALL", "PENDING", "COMPLETED"
    var taskFilter by remember { mutableStateOf("ALL") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(Color(0xFF0F1115))
            .testTag("roadmap_management_view")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- TOP HORIZONTAL BAR: LỘ TRÌNH SWITCHER ---
            Surface(
                color = Color(0xFF161920),
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📅",
                                fontSize = 20.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Lộ trình & Kế hoạch Đa tháng",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Button(
                            onClick = {
                                initialAiPromptForDialog = null
                                showAiPlannerDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_open_ai_roadmap_planner")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tạo với AI", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // List of roadmaps chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(roadmaps) { roadmap ->
                            val isSelected = selectedRoadmap?.id == roadmap.id
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) Color(0xFF065F46) else Color(0xFF232732),
                                border = if (isSelected) BorderStroke(1.dp, Color(0xFF34D399)) else BorderStroke(0.5.dp, Color(0xFF374151)),
                                modifier = Modifier
                                    .clickable { viewModel.selectRoadmap(roadmap.id) }
                                    .testTag("roadmap_chip_${roadmap.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(roadmap.icon, fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                                    Text(
                                        text = roadmap.title,
                                        color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1F2937),
                                border = BorderStroke(1.dp, Color(0xFF4B5563)),
                                modifier = Modifier
                                    .clickable {
                                        initialAiPromptForDialog = null
                                        showAiPlannerDialog = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lộ trình mới", color = Color(0xFF34D399), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // --- MAIN CONTENT ---
            if (roadmaps.isEmpty()) {
                // Empty state with inspiration presets
                RoadmapEmptyState(
                    onSelectPreset = { presetPrompt ->
                        initialAiPromptForDialog = presetPrompt
                        showAiPlannerDialog = true
                    },
                    onOpenAiChat = {
                        initialAiPromptForDialog = null
                        showAiPlannerDialog = true
                    }
                )
            } else if (selectedRoadmap != null) {
                val current = selectedRoadmap!!
                val completedCount = tasks.count { it.isCompleted }
                val totalCount = tasks.size
                val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

                val filteredTasks = when (taskFilter) {
                    "PENDING" -> tasks.filter { !it.isCompleted }
                    "COMPLETED" -> tasks.filter { it.isCompleted }
                    else -> tasks
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("roadmap_tasks_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Card of Active Roadmap
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1F2C)),
                            border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0xFF065F46)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(current.icon, fontSize = 24.sp)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = current.title,
                                                color = Color.White,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF047857).copy(alpha = 0.3f)
                                            ) {
                                                Text(
                                                    text = current.category,
                                                    color = Color(0xFF6EE7B7),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                roadmapToEdit = current
                                                showEditRoadmapDialog = true
                                            }
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Chỉnh sửa", tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { showDeleteConfirmDialog = current }
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Xóa", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                if (current.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = current.description,
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Date & Days badges
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Bắt đầu: ${formatDateDisplay(current.startDate)}",
                                            color = Color(0xFFE5E7EB),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Tổng: ${current.totalDays} ngày",
                                            color = Color(0xFFE5E7EB),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Progress Bar
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Tiến độ thực hiện", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                                        Text(
                                            "$completedCount/$totalCount hoàn thành (${(progress * 100).toInt()}%)",
                                            color = Color(0xFF34D399),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFF10B981),
                                        trackColor = Color(0xFF2E384D)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Action Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            initialAiPromptForDialog = "Tôi đang thực hiện lộ trình: ${current.title}. Hãy giúp tôi tối ưu hoặc tư vấn thêm các lưu ý quan trọng:"
                                            showAiPlannerDialog = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFF059669)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399))
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Chat cùng AI", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            taskToEdit = null
                                            showTaskDialog = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669), contentColor = Color.White)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Thêm việc", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Filter Chips Bar
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = taskFilter == "ALL",
                                onClick = { taskFilter = "ALL" },
                                label = { Text("Tất cả ($totalCount)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF047857),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1F2937),
                                    labelColor = Color(0xFF9CA3AF)
                                )
                            )
                            FilterChip(
                                selected = taskFilter == "PENDING",
                                onClick = { taskFilter = "PENDING" },
                                label = { Text("Cần làm (${totalCount - completedCount})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF047857),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1F2937),
                                    labelColor = Color(0xFF9CA3AF)
                                )
                            )
                            FilterChip(
                                selected = taskFilter == "COMPLETED",
                                onClick = { taskFilter = "COMPLETED" },
                                label = { Text("Đã xong ($completedCount)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF047857),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1F2937),
                                    labelColor = Color(0xFF9CA3AF)
                                )
                            )
                        }
                    }

                    // Task Items grouped by Phase or chronological
                    if (filteredTasks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (taskFilter == "COMPLETED") "Chưa có công việc nào hoàn thành." else "Không có công việc nào trong mục này.",
                                    color = Color(0xFF6B7280),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        // Group by Phase
                        val groupedTasks = filteredTasks.groupBy { it.phase }
                        groupedTasks.forEach { (phaseName, phaseTasks) ->
                            item {
                                Text(
                                    text = phaseName,
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }

                            items(phaseTasks, key = { it.id }) { task ->
                                RoadmapTaskCard(
                                    task = task,
                                    onToggleCompleted = { viewModel.toggleRoadmapTaskCompleted(task, context) },
                                    onEdit = {
                                        taskToEdit = task
                                        showTaskDialog = true
                                    },
                                    onDelete = { taskToDeleteConfirm = task },
                                    onToggleReminder = {
                                        val updated = task.copy(hasReminder = !task.hasReminder)
                                        viewModel.updateRoadmapTask(updated, context)
                                        Toast.makeText(
                                            context,
                                            if (updated.hasReminder) "Đã bật nhắc nhở cho việc này" else "Đã tắt nhắc nhở",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }

    // --- DIALOGS ---
    if (showAiPlannerDialog) {
        AiRoadmapPlannerDialog(
            viewModel = viewModel,
            initialPrompt = initialAiPromptForDialog,
            onDismiss = { showAiPlannerDialog = false },
            onRoadmapSaved = {
                showAiPlannerDialog = false
            }
        )
    }

    if (showTaskDialog && selectedRoadmap != null) {
        AddEditRoadmapTaskDialog(
            roadmap = selectedRoadmap!!,
            task = taskToEdit,
            onDismiss = { showTaskDialog = false },
            onSave = { newTask ->
                if (taskToEdit == null) {
                    viewModel.addCustomRoadmapTask(newTask, context)
                } else {
                    viewModel.updateRoadmapTask(newTask, context)
                }
                showTaskDialog = false
            }
        )
    }

    if (showEditRoadmapDialog && roadmapToEdit != null) {
        EditRoadmapInfoDialog(
            roadmap = roadmapToEdit!!,
            onDismiss = { showEditRoadmapDialog = false },
            onSave = { updated ->
                viewModel.updateRoadmap(updated, context)
                showEditRoadmapDialog = false
            }
        )
    }

    if (showDeleteConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Xác nhận xóa lộ trình") },
            text = { Text("Bạn có chắc chắn muốn xóa toàn bộ lộ trình '${showDeleteConfirmDialog?.title}' cùng tất cả các mốc công việc và báo thức liên quan?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = showDeleteConfirmDialog
                        showDeleteConfirmDialog = null
                        if (toDelete != null) {
                            viewModel.deleteRoadmap(toDelete, context)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("Xóa lộ trình", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    if (taskToDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { taskToDeleteConfirm = null },
            title = { Text("Xóa công việc") },
            text = { Text("Bạn có muốn xóa mốc công việc '${taskToDeleteConfirm?.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = taskToDeleteConfirm
                        taskToDeleteConfirm = null
                        if (t != null) {
                            viewModel.deleteRoadmapTask(t, context)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("Xóa", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDeleteConfirm = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun RoadmapTaskCard(
    task: RoadmapTask,
    onToggleCompleted: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleReminder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDone = task.isCompleted
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) Color(0xFF14171E) else Color(0xFF1E222D)
        ),
        border = BorderStroke(
            1.dp,
            if (isDone) Color(0xFF262E3D) else if (task.priority == "Cao") Color(0xFFDC2626).copy(alpha = 0.5f) else Color(0xFF333D50)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("roadmap_task_card_${task.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox
                Checkbox(
                    checked = isDone,
                    onCheckedChange = { onToggleCompleted() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF10B981),
                        uncheckedColor = Color(0xFF6B7280),
                        checkmarkColor = Color.White
                    ),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Ngày ${task.dayOffset + 1} (${formatDateDisplay(task.scheduledDate)})",
                                color = Color(0xFF6EE7B7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF374151)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = task.scheduledTime,
                                    color = Color(0xFFD1D5DB),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (task.priority == "Cao") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF7F1D1D).copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "Ưu tiên cao",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = task.title,
                        color = if (isDone) Color(0xFF6B7280) else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                    )
                }

                IconButton(
                    onClick = onToggleReminder,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (task.hasReminder) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = "Nhắc nhở",
                        tint = if (task.hasReminder) Color(0xFFFBBF24) else Color(0xFF4B5563),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (task.instruction.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = task.instruction,
                    color = if (isDone) Color(0xFF4B5563) else Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Divider(color = Color(0xFF262E3D), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chỉnh sửa", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xóa", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoadmapEmptyState(
    onSelectPreset: (String) -> Unit,
    onOpenAiChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF10B981).copy(alpha = 0.3f), Color(0xFF047857).copy(alpha = 0.05f))
                        )
                    )
                    .border(2.dp, Color(0xFF34D399), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🌱", fontSize = 38.sp)
            }
        }

        item {
            Text(
                text = "Lập Lộ trình & Lịch trình Đa tháng với AI",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Bạn muốn theo dõi quy trình trồng trọt nhiều tháng, phượt xuyên Việt 1 tháng, kế hoạch học tập hay chăm sóc gia đình? AI sẽ chia nhỏ từng mốc ngày, giờ và hẹn giờ báo thức chuẩn xác!",
                color = Color(0xFF9CA3AF),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        item {
            Button(
                onClick = onOpenAiChat,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_empty_create_ai_roadmap")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chat với AI để lập kế hoạch ngay", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "💡 GỢI Ý MẪU LỘ TRÌNH PHỔ BIẾN (NHẤN ĐỂ TẠO NHANH)",
                color = Color(0xFF6EE7B7),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val presets = listOf(
            Triple("🌽 Trồng ngô lai năng suất cao (90 ngày)", "Lập lịch trình canh tác và bón phân cho vụ trồng ngô lai 90 ngày từ làm đất, bón lót, tỉa dặm đến thu hoạch.", "Nông nghiệp"),
            Triple("🛵 Phượt Xuyên Việt 30 ngày (Bắc - Nam)", "Lập lịch trình du lịch phượt khắp Việt Nam trong vòng 1 tháng từ Hà Nội qua các tỉnh miền Trung đến Đất Mũi Cà Mau.", "Du lịch"),
            Triple("🌾 Canh tác lúa nước chất lượng cao (100 ngày)", "Tạo lịch chăm sóc lúa nước 100 ngày gồm các đợt bón phân đón đòng, phòng sâu rầy và ngày gặt.", "Nông nghiệp"),
            Triple("📚 Tự học Tiếng Anh giao tiếp cấp tốc (60 ngày)", "Lập lộ trình tự học tiếng Anh giao tiếp và từ vựng trong vòng 2 tháng.", "Học tập"),
            Triple("🏋️ Lộ trình Giảm mỡ & Rèn thể lực (60 ngày)", "Lập kế hoạch tập luyện thể thao và dinh dưỡng cải thiện sức khỏe trong 2 tháng.", "Sức khỏe")
        )

        items(presets) { (title, prompt, category) ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222D)),
                border = BorderStroke(1.dp, Color(0xFF374151)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPreset(prompt) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = prompt,
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddEditRoadmapTaskDialog(
    roadmap: Roadmap,
    task: RoadmapTask?,
    onDismiss: () -> Unit,
    onSave: (RoadmapTask) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(task?.title ?: "") }
    var instruction by remember { mutableStateOf(task?.instruction ?: "") }
    var phase by remember { mutableStateOf(task?.phase ?: "Giai đoạn 1") }
    var scheduledDate by remember { mutableStateOf(task?.scheduledDate ?: roadmap.startDate) }
    var scheduledTime by remember { mutableStateOf(task?.scheduledTime ?: "08:00") }
    var priority by remember { mutableStateOf(task?.priority ?: "Bình thường") }
    var hasReminder by remember { mutableStateOf(task?.hasReminder ?: true) }
    var hasLoudAlarm by remember { mutableStateOf(task?.hasLoudAlarm ?: false) }

    val cal = Calendar.getInstance()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "Thêm công việc vào lộ trình" else "Chỉnh sửa công việc") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên công việc") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phase,
                    onValueChange = { phase = it },
                    label = { Text("Chặng / Giai đoạn (ví dụ: Chặng Miền Trung, Bón thúc...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Date button
                    OutlinedButton(
                        onClick = {
                            val parts = scheduledDate.split("-")
                            val y = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
                            val m = (parts.getOrNull(1)?.toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1
                            val d = parts.getOrNull(2)?.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)

                            DatePickerDialog(context, { _, year, month, dayOfMonth ->
                                scheduledDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                            }, y, m, d).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(formatDateDisplay(scheduledDate), fontSize = 12.sp)
                    }

                    // Time button
                    OutlinedButton(
                        onClick = {
                            val parts = scheduledTime.split(":")
                            val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
                            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

                            TimePickerDialog(context, { _, hourOfDay, minute ->
                                scheduledTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                            }, h, m, true).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(scheduledTime, fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("Hướng dẫn / Lưu ý chi tiết") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bật chuông / thông báo nhắc nhở", fontSize = 13.sp)
                    Switch(
                        checked = hasReminder,
                        onCheckedChange = { hasReminder = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Chuông báo thức to (quan trọng)", fontSize = 13.sp)
                    Switch(
                        checked = hasLoudAlarm,
                        onCheckedChange = { hasLoudAlarm = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập tên công việc", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val startCal = Calendar.getInstance()
                    val targetCal = Calendar.getInstance()
                    try {
                        startCal.time = sdf.parse(roadmap.startDate) ?: Date()
                        targetCal.time = sdf.parse(scheduledDate) ?: Date()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    val diffDays = ((targetCal.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)

                    val updated = task?.copy(
                        title = title.trim(),
                        instruction = instruction.trim(),
                        phase = phase.trim(),
                        scheduledDate = scheduledDate,
                        scheduledTime = scheduledTime,
                        dayOffset = diffDays,
                        priority = priority,
                        hasReminder = hasReminder,
                        hasLoudAlarm = hasLoudAlarm
                    ) ?: RoadmapTask(
                        roadmapId = roadmap.id,
                        dayOffset = diffDays,
                        scheduledDate = scheduledDate,
                        scheduledTime = scheduledTime,
                        title = title.trim(),
                        instruction = instruction.trim(),
                        phase = phase.trim(),
                        priority = priority,
                        hasReminder = hasReminder,
                        hasLoudAlarm = hasLoudAlarm
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("Lưu công việc", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
fun EditRoadmapInfoDialog(
    roadmap: Roadmap,
    onDismiss: () -> Unit,
    onSave: (Roadmap) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(roadmap.title) }
    var description by remember { mutableStateOf(roadmap.description) }
    var category by remember { mutableStateOf(roadmap.category) }
    var icon by remember { mutableStateOf(roadmap.icon) }
    var totalDaysStr by remember { mutableStateOf(roadmap.totalDays.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉnh sửa lộ trình") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { icon = it },
                        label = { Text("Icon") },
                        modifier = Modifier.width(70.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Tên lộ trình") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Lĩnh vực (Nông nghiệp, Du lịch, Học tập...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = totalDaysStr,
                    onValueChange = { totalDaysStr = it },
                    label = { Text("Tổng số ngày") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả tóm tắt") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập tên lộ trình", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val days = totalDaysStr.toIntOrNull() ?: roadmap.totalDays
                    val updated = roadmap.copy(
                        title = title.trim(),
                        icon = icon.ifBlank { "🌱" },
                        category = category.ifBlank { "Khác" },
                        totalDays = days,
                        description = description.trim()
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("Lưu thay đổi", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

fun formatDateDisplay(dateStr: String): String {
    return try {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdfIn.parse(dateStr)
        if (date != null) {
            val sdfOut = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            sdfOut.format(date)
        } else {
            dateStr
        }
    } catch (e: Exception) {
        dateStr
    }
}
