package com.example.ui

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ShiftDayAssignment
import com.example.data.ShiftRotationConfig
import com.example.data.ShiftStation
import com.example.utils.ShiftRotationManager
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftRotationDialog(
    viewModel: NotesViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentConfig by viewModel.shiftRotationConfig.collectAsStateWithLifecycle()
    val stations by viewModel.allShiftStations.collectAsStateWithLifecycle()

    var isEnabled by remember { mutableStateOf(currentConfig.isEnabled) }
    var cycleWeeks by remember { mutableStateOf(currentConfig.cycleWeeks) }
    var switchTimeStr by remember { mutableStateOf(currentConfig.switchTimeStr) }
    var notifyOnSwitch by remember { mutableStateOf(currentConfig.notifyOnSwitch) }
    var anchorMondayMillis by remember { mutableStateOf(currentConfig.anchorMondayMillis) }

    val defaultStation1 = stations.firstOrNull()?.name ?: "Nghỉ"
    val defaultStation2 = stations.getOrNull(1)?.name ?: defaultStation1

    // Assignments map: key = "${weekIndex}_${dayOfWeek}", value = stationName
    var assignmentsMap by remember(stations, currentConfig) {
        val map = mutableStateMapOf<String, String>()
        val existingNames = stations.map { it.name.trim().lowercase() }.toSet()

        currentConfig.assignments.forEach { a ->
            val targetLower = a.targetStationName.trim().lowercase()
            if (targetLower in existingNames) {
                val matched = stations.find { it.name.trim().equals(a.targetStationName.trim(), ignoreCase = true) }
                map["${a.weekIndex}_${a.dayOfWeek}"] = matched?.name ?: a.targetStationName
            } else if (targetLower == "nghỉ" || targetLower == "ngày nghỉ") {
                map["${a.weekIndex}_${a.dayOfWeek}"] = "Nghỉ"
            } else {
                // If it was legacy default name that no longer exists in stations
                val fallback = if (a.dayOfWeek in 6..7) "Nghỉ" else (if (a.weekIndex == 0) defaultStation1 else defaultStation2)
                map["${a.weekIndex}_${a.dayOfWeek}"] = fallback
            }
        }
        // Fill defaults if empty
        if (map.isEmpty()) {
            for (day in 1..5) map["0_$day"] = defaultStation1
            map["0_6"] = "Nghỉ"
            map["0_7"] = "Nghỉ"
            for (day in 1..5) map["1_$day"] = defaultStation2
            map["1_6"] = "Nghỉ"
            map["1_7"] = "Nghỉ"
        }
        mutableStateOf(map)
    }

    var selectedWeekTab by remember { mutableStateOf(0) } // 0 = Tuần 1, 1 = Tuần 2
    var stationPickerDay by remember { mutableStateOf<Int?>(null) } // Day currently choosing station for

    val now = Calendar.getInstance()
    val thisMonday = remember { ShiftRotationManager.getMondayOfDate(System.currentTimeMillis()) }
    val lastMonday = remember { thisMonday - 7L * 24 * 60 * 60 * 1000L }

    val currentCalculatedWeek = remember(anchorMondayMillis, cycleWeeks) {
        ShiftRotationManager.getWeekIndex(anchorMondayMillis, System.currentTimeMillis(), cycleWeeks)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🔄", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Chu kỳ xoay ca tự động",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tự động luân chuyển các ca làm đã tạo theo tuần",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (stations.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF78350F).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Chưa có ca làm việc nào được tạo. Hãy nhấn '+ Thêm' ở màn hình chính để tạo ca trước khi xếp lịch.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFDE68A)
                                )
                            }
                        }
                    }

                    // Master Switch
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isEnabled) Color(0xFF064E3B).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (isEnabled) Color(0xFF10B981).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isEnabled = !isEnabled }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bật tự động xoay ca hàng ngày",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isEnabled) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isEnabled) "Đang chạy • Tự động kích hoạt ca vào $switchTimeStr" else "Đang tắt • Cần bấm thủ công để bắt đầu ca",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { isEnabled = it }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Switch time and Current Week selection
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Time Picker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Giờ chuyển ca mỗi ngày:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                OutlinedButton(
                                    onClick = {
                                        val parts = switchTimeStr.replace("h", ":").split(":")
                                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 6
                                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                switchTimeStr = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                                            },
                                            h,
                                            m,
                                            true
                                        ).show()
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(switchTimeStr, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Anchor week selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Tuần này đang là:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = currentCalculatedWeek == 0,
                                        onClick = { anchorMondayMillis = thisMonday },
                                        label = { Text("Tuần 1", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = currentCalculatedWeek == 1,
                                        onClick = { anchorMondayMillis = lastMonday },
                                        label = { Text("Tuần 2", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Phân bổ ca theo tuần:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = {
                                if (stations.isEmpty()) {
                                    Toast.makeText(context, "Chưa có ca làm việc nào. Vui lòng tạo ca làm trước!", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                val s1 = stations.first().name
                                val s2 = stations.getOrNull(1)?.name ?: s1
                                val map = mutableStateMapOf<String, String>()
                                for (day in 1..5) map["0_$day"] = s1
                                map["0_6"] = "Nghỉ"
                                map["0_7"] = "Nghỉ"
                                for (day in 1..5) map["1_$day"] = s2
                                map["1_6"] = "Nghỉ"
                                map["1_7"] = "Nghỉ"
                                assignmentsMap = map
                                val desc = if (stations.size >= 2) "Tuần 1: $s1 • Tuần 2: $s2 (T7-CN nghỉ)" else "Tuần 1 & 2: $s1 (T7-CN nghỉ)"
                                Toast.makeText(context, "Đã áp dụng mẫu: $desc", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("⚡ Áp dụng mẫu chuẩn", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Week Tabs (Tuần 1 / Tuần 2)
                    TabRow(
                        selectedTabIndex = selectedWeekTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedWeekTab == 0,
                            onClick = { selectedWeekTab = 0 },
                            text = { Text("Tuần 1 ${if (currentCalculatedWeek == 0) "(Hiện tại)" else ""}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedWeekTab == 1,
                            onClick = { selectedWeekTab = 1 },
                            text = { Text("Tuần 2 ${if (currentCalculatedWeek == 1) "(Hiện tại)" else ""}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Day by Day list for selected week
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (day in 1..7) {
                            val dayName = ShiftRotationManager.getDayName(day)
                            val key = "${selectedWeekTab}_$day"
                            val assignedStationName = assignmentsMap[key] ?: if (day in 1..5) (if (selectedWeekTab == 0) defaultStation1 else defaultStation2) else "Nghỉ"

                            val isWeekend = day in 6..7
                            val matchedStation = stations.find { it.name.trim().equals(assignedStationName.trim(), ignoreCase = true) }
                            val isOff = matchedStation == null && assignedStationName.trim().equals("Nghỉ", ignoreCase = true)

                            val badgeColor = if (isOff) Color(0xFF10B981) else Color(0xFFF59E0B)
                            val displayLabel = if (matchedStation != null) {
                                "${matchedStation.icon} ${matchedStation.name}"
                            } else if (isOff) {
                                "🛌 Nghỉ"
                            } else {
                                "🏭 $assignedStationName"
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        stationPickerDay = day
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = dayName,
                                            fontWeight = if (isWeekend) FontWeight.Normal else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isWeekend) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isWeekend) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("(Cuối tuần)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = badgeColor.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = displayLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeColor
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = badgeColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val configToSave = buildConfig(
                                isEnabled = isEnabled,
                                cycleWeeks = cycleWeeks,
                                anchorMondayMillis = anchorMondayMillis,
                                switchTimeStr = switchTimeStr,
                                notifyOnSwitch = notifyOnSwitch,
                                assignmentsMap = assignmentsMap,
                                stations = stations
                            )
                            viewModel.saveShiftRotationConfig(context, configToSave)
                            Toast.makeText(
                                context,
                                if (isEnabled) "✅ Đã lưu và bật chu kỳ xoay ca tự động!" else "Đã lưu cấu hình xoay ca",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("save_shift_rotation_config_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lưu cấu hình xoay ca",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Test run & force apply today
                                val configToSave = buildConfig(
                                    isEnabled = isEnabled,
                                    cycleWeeks = cycleWeeks,
                                    anchorMondayMillis = anchorMondayMillis,
                                    switchTimeStr = switchTimeStr,
                                    notifyOnSwitch = notifyOnSwitch,
                                    assignmentsMap = assignmentsMap,
                                    stations = stations
                                )
                                viewModel.saveShiftRotationConfig(context, configToSave)
                                Toast.makeText(context, "⚡ Đã kích hoạt ca hôm nay theo chu kỳ!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Kích hoạt hôm nay",
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Hủy",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet / Dialog to pick station for selected day
    if (stationPickerDay != null) {
        val targetDay = stationPickerDay!!
        val dayName = ShiftRotationManager.getDayName(targetDay)

        // Build list of options: ONLY user's actual created stations in app + "Nghỉ"
        data class ShiftOptionItem(val name: String, val icon: String, val isOff: Boolean)
        val options = mutableListOf<ShiftOptionItem>()
        stations.forEach { st ->
            if (options.none { it.name.trim().equals(st.name.trim(), ignoreCase = true) }) {
                options.add(ShiftOptionItem(st.name, st.icon, false))
            }
        }
        if (options.none { it.name.trim().equals("Nghỉ", ignoreCase = true) }) {
            options.add(ShiftOptionItem("Nghỉ", "🛌", true))
        }

        val currentVal = assignmentsMap["${selectedWeekTab}_$targetDay"]
            ?: if (targetDay in 1..5) (if (selectedWeekTab == 0) defaultStation1 else defaultStation2) else "Nghỉ"

        AlertDialog(
            onDismissRequest = { stationPickerDay = null },
            title = {
                Text(
                    text = "Chọn ca cho $dayName (Tuần ${selectedWeekTab + 1})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stations.isEmpty()) {
                        Text(
                            text = "Chưa có ca làm việc nào. Hãy tạo ca trong màn hình Quản lý ca làm.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    options.forEach { optionItem ->
                        val isSelected = currentVal.trim().equals(optionItem.name.trim(), ignoreCase = true)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    assignmentsMap["${selectedWeekTab}_$targetDay"] = optionItem.name
                                    stationPickerDay = null
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = optionItem.icon,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = optionItem.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { stationPickerDay = null }) {
                    Text("Đóng")
                }
            }
        )
    }
}

private fun buildConfig(
    isEnabled: Boolean,
    cycleWeeks: Int,
    anchorMondayMillis: Long,
    switchTimeStr: String,
    notifyOnSwitch: Boolean,
    assignmentsMap: Map<String, String>,
    stations: List<ShiftStation>
): ShiftRotationConfig {
    val list = mutableListOf<ShiftDayAssignment>()
    for (w in 0 until cycleWeeks) {
        for (d in 1..7) {
            val defaultName = if (d in 6..7) "Nghỉ" else (stations.firstOrNull()?.name ?: "Nghỉ")
            val stName = assignmentsMap["${w}_$d"] ?: defaultName
            val matchedStation = stations.find { it.name.trim().equals(stName.trim(), ignoreCase = true) }
            list.add(
                ShiftDayAssignment(
                    weekIndex = w,
                    dayOfWeek = d,
                    stationId = matchedStation?.id,
                    targetStationName = if (matchedStation != null) matchedStation.name else "Nghỉ"
                )
            )
        }
    }
    return ShiftRotationConfig(
        isEnabled = isEnabled,
        cycleWeeks = cycleWeeks,
        anchorMondayMillis = anchorMondayMillis,
        switchTimeStr = switchTimeStr,
        notifyOnSwitch = notifyOnSwitch,
        assignments = list
    )
}
