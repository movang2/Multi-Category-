package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ShiftStation
import com.example.data.ShiftTask
import java.text.SimpleDateFormat
import java.util.*

// --- Palette màu giao diện Dark Mode theo yêu cầu ---
private val DarkBg = Color(0xFF17181B)
private val DarkCard = Color(0xFF232529)
private val DarkSubCard = Color(0xFF2B2E33)
private val DarkBorder = Color(0xFF3A3E45)
private val ShiftBlue = Color(0xFF075D8C)
private val ShiftLightBlue = Color(0xFF38BDF8)
private val StartShiftBtnBg = Color(0xFFE0F2FE) // Nền xanh rất nhạt / xanh trắng
private val StartShiftBtnText = Color(0xFF075D8C) // Chữ xanh đậm
private val TextPrimary = Color(0xFFF3F4F6) // Trắng/xám rất sáng
private val TextSecondary = Color(0xFFAEB4BD) // Xám #AEB4BD

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftManagementView(
    viewModel: NotesViewModel,
    innerPadding: PaddingValues
) {
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Đã cấp quyền thông báo thành công!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Cần quyền thông báo để chuông báo ca làm hoạt động!", Toast.LENGTH_LONG).show()
        }
    }

    val checkAndRequestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val stations by viewModel.allShiftStations.collectAsStateWithLifecycle()
    val selectedStation by viewModel.selectedStation.collectAsStateWithLifecycle()
    val tasks by viewModel.tasksForSelectedStation.collectAsStateWithLifecycle()
    val startedStationIds by viewModel.startedStationIds.collectAsStateWithLifecycle()
    val activeShiftTime by viewModel.activeShiftTime.collectAsStateWithLifecycle()
    val shiftRotationConfig by viewModel.shiftRotationConfig.collectAsStateWithLifecycle()

    // Dialog states
    var showAddStationDialog by remember { mutableStateOf(false) }
    var stationToEdit by remember { mutableStateOf<ShiftStation?>(null) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<ShiftTask?>(null) }
    var showAiPlannerDialog by remember { mutableStateOf(false) }
    var showRotationDialog by remember { mutableStateOf(false) }
    var showRescheduleDialog by remember { mutableStateOf(false) }
    var showStartShiftAlignDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(DarkBg)
    ) {
        // ==================================================
        // 1. KHU VỰC "VỊ TRÍ / MÁY LÀM VIỆC"
        // ==================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp)
        ) {
            // Hàng đầu: Vị trí + 🔔 + Lập lịch AI + Xoay ca + Thêm
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vị trí",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    softWrap = false
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Các nút bên phải: tự co giãn, cuộn ngang nhẹ nếu màn hình hẹp
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            checkAndRequestNotificationPermission()
                            viewModel.triggerTestNotification(
                                context = context,
                                title = "Nhắc nhở ca làm",
                                message = "🔔 Thông báo hệ thống Android đang hoạt động bình thường!"
                            )
                            Toast.makeText(context, "Đã phát thông báo hệ thống!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = "Thử thông báo",
                            tint = ShiftLightBlue,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    FilledTonalButton(
                        onClick = { showAiPlannerDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkSubCard,
                            contentColor = ShiftLightBlue
                        ),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("ai_schedule_planner_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lập lịch AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { showRotationDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (shiftRotationConfig.isEnabled) Color(0xFF1E3A8A) else DarkSubCard,
                            contentColor = if (shiftRotationConfig.isEnabled) Color(0xFF93C5FD) else ShiftLightBlue
                        ),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("shift_rotation_config_button")
                    ) {
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (shiftRotationConfig.isEnabled) "Xoay ca: BẬT" else "Xoay ca",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            stationToEdit = null
                            showAddStationDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Thêm", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Bên dưới là các tab: ✓ 🏭 ca ngày    🏭 ca đêm    🏭 ngày nghỉ
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stations) { station ->
                    val isSelected = station.id == selectedStation?.id
                    val isStationStarted = startedStationIds.contains(station.id)

                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = if (isSelected) ShiftBlue else DarkCard,
                        border = if (isSelected) null else BorderStroke(1.dp, DarkBorder),
                        contentColor = if (isSelected) Color.White else TextSecondary,
                        modifier = Modifier
                            .clickable {
                                if (isSelected) {
                                    stationToEdit = station
                                    showAddStationDialog = true
                                } else {
                                    viewModel.selectStation(station.id, context)
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = "${if (isStationStarted) "🔥 " else ""}${station.icon} ${station.name}",
                                fontSize = 13.sp,
                                fontWeight = if (isSelected || isStationStarted) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextSecondary
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Chỉnh sửa vị trí",
                                    modifier = Modifier.size(13.dp),
                                    tint = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // Banner trạng thái chu kỳ xoay ca tự động nếu đang bật
            if (shiftRotationConfig.enabled) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F2744),
                    border = BorderStroke(1.dp, Color(0xFF1D4ED8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 3.dp)
                        .clickable { showRotationDialog = true }
                        .testTag("rotation_status_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Autorenew,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Chu kỳ xoay ca tự động đang BẬT",
                                color = Color(0xFF93C5FD),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tự động đổi ca lúc ${shiftRotationConfig.switchTimeStr} mỗi ngày • Chạm để tùy chỉnh",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ==================================================
        // 2. CARD "VỊ TRÍ ĐANG CHỌN"
        // ==================================================
        selectedStation?.let { station ->
            val isCurrentStationStarted = startedStationIds.contains(station.id)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, if (isCurrentStationStarted) ShiftBlue.copy(alpha = 0.6f) else DarkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Hàng tên ca + Nút sửa + Nút BẮT ĐẦU CA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "${if (isCurrentStationStarted) "🔥 " else ""}${station.icon} ${station.name}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                onClick = {
                                    stationToEdit = station
                                    showAddStationDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSubCard,
                                border = BorderStroke(1.dp, DarkBorder),
                                contentColor = ShiftLightBlue,
                                modifier = Modifier.testTag("edit_station_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Chỉnh sửa vị trí",
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Sửa",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        val firstTask = tasks.firstOrNull()
                        val isFirstTaskPassed = firstTask?.let {
                            val parsed = viewModel.parseHourMinute(it.timeScheduled)
                            if (parsed != null) {
                                val cal = Calendar.getInstance()
                                val curMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                                val taskMinutes = parsed.first * 60 + parsed.second
                                (curMinutes - taskMinutes) > 3
                            } else false
                        } ?: false

                        // "BẮT ĐẦU CA" là nút nổi bật nhất: Nền xanh rất nhạt, chữ xanh đậm
                        Button(
                            onClick = {
                                if (isCurrentStationStarted) {
                                    viewModel.endShiftForStation(station.id, context)
                                    Toast.makeText(context, "Đã kết thúc ca làm cho ${station.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (isFirstTaskPassed) {
                                        showStartShiftAlignDialog = true
                                    } else {
                                        checkAndRequestNotificationPermission()
                                        viewModel.startShiftForStation(station.id, context = context, resetCompleted = true)
                                        Toast.makeText(context, "Đã bắt đầu ca cho ${station.name}!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCurrentStationStarted) Color(0xFFDC2626) else StartShiftBtnBg,
                                contentColor = if (isCurrentStationStarted) Color.White else StartShiftBtnText
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(
                                text = if (isCurrentStationStarted) "KẾT THÚC CA" else "BẮT ĐẦU CA",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Đường kẻ phân cách nhẹ bên trong card
                    if (tasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Hàng dưới: [⚡ Giờ hiện tại]   [◷ Dời mốc giờ]         ✨ Lập lịch AI
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                        val curNow = timeFormat.format(Calendar.getInstance().time)
                                        viewModel.rescheduleStationTasks(context, station.id, curNow, resetCompleted = true)
                                        Toast.makeText(context, "Đã dời mốc bắt đầu từ BÂY GIỜ ($curNow)!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, DarkBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFFFBBF24))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("⚡ Giờ hiện tại", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }

                                OutlinedButton(
                                    onClick = { showRescheduleDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, DarkBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("reschedule_shift_button")
                                ) {
                                    Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(13.dp), tint = ShiftLightBlue)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("◷ Dời mốc giờ", fontSize = 11.sp)
                                }
                            }

                            TextButton(
                                onClick = { showAiPlannerDialog = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp), tint = ShiftLightBlue)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("✨ Lập lịch AI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ShiftLightBlue)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ==================================================
        // 3. VÙNG NỘI DUNG DANH SÁCH CÔNG VIỆC + FAB
        // Không còn tab Bàn giao ca; thanh lịch trình nằm hoàn toàn phía trên danh sách
        // ==================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = TextSecondary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Chưa có việc cần làm cho vị trí này",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    taskToEdit = null
                                    showAddTaskDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ShiftBlue)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thêm việc")
                            }
                            FilledTonalButton(
                                onClick = { showAiPlannerDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSubCard, contentColor = ShiftLightBlue)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lập lịch AI")
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ==================================================
                    // THANH THÔNG TIN LỊCH TRÌNH
                    // Nằm hoàn toàn độc lập phía trên LazyColumn, không overlay,
                    // có nền DarkBg vững chắc để khi scroll không bị xuyên thấu.
                    // ==================================================
                    Surface(
                        color = DarkBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val timeSpan = if (tasks.size >= 2) " (${tasks.first().timeScheduled} → ${tasks.last().timeScheduled})" else ""
                            Text(
                                text = "Lịch trình ${tasks.size} bước$timeSpan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val allLoudInStation = tasks.isNotEmpty() && tasks.all { it.hasLoudAlarm }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (allLoudInStation) Color(0xFF7F1D1D) else DarkSubCard,
                                    border = BorderStroke(1.dp, if (allLoudInStation) Color(0xFFEF4444) else DarkBorder),
                                    modifier = Modifier.clickable {
                                        selectedStation?.let { st ->
                                            viewModel.setStationAllTasksLoudAlarm(st.id, !allLoudInStation, context)
                                            Toast.makeText(
                                                context,
                                                if (!allLoudInStation) "Đã bật chuông báo to cho mọi công việc ${st.name} (Kho cá/Nấu nướng)"
                                                else "Đã chuyển toàn bộ sang thông báo êm",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            if (allLoudInStation) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = if (allLoudInStation) Color(0xFFFCA5A5) else TextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (allLoudInStation) "🔊 Chuông to" else "🔕 Chuông êm",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (allLoudInStation) Color(0xFFFCA5A5) else TextSecondary
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkSubCard,
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier
                                        .clickable { showRescheduleDialog = true }
                                        .testTag("bar_reschedule_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(13.dp), tint = ShiftLightBlue)
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("↻ Căn", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkSubCard,
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.clickable {
                                        taskToEdit = null
                                        showAddTaskDialog = true
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp), tint = TextPrimary)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Thêm", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }

                    // ==================================================
                    // DANH SÁCH CÔNG VIỆC
                    // LazyColumn bắt đầu BÊN DƯỚI thanh lịch trình,
                    // có contentPadding(bottom = 96.dp) để task cuối không bị che bởi FAB và Bottom Navigation!
                    // ==================================================
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            ShiftTaskItemRow(
                                task = task,
                                onToggleHidden = {
                                    viewModel.toggleTaskHidden(context, task)
                                    Toast.makeText(
                                        context,
                                        if (!task.isHidden) "Đã ẩn: Công việc này sẽ không phát thông báo!"
                                        else "Đã hiện: Sẽ phát thông báo khi bắt đầu ca!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onDelete = { viewModel.deleteShiftTask(context, task) },
                                onEdit = {
                                    taskToEdit = task
                                    showAddTaskDialog = true
                                },
                                onToggleLoudAlarm = { viewModel.toggleTaskLoudAlarm(task, context) }
                            )
                        }
                    }
                }
            }

            // ==================================================
            // FAB THÊM VIỆC (✓＋)
            // ==================================================
            FloatingActionButton(
                onClick = {
                    taskToEdit = null
                    showAddTaskDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .testTag("add_shift_action_fab"),
                shape = RoundedCornerShape(16.dp),
                containerColor = ShiftBlue,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddTask,
                    contentDescription = "Thêm việc"
                )
            }
        }
    }

    // --- DIALOG: Add/Edit Station ---
    if (showAddStationDialog) {
        AddStationDialog(
            station = stationToEdit,
            onDismiss = { showAddStationDialog = false },
            onSave = { name, icon, desc, qr ->
                viewModel.saveShiftStation(
                    id = stationToEdit?.id,
                    name = name,
                    icon = icon,
                    description = desc,
                    qrCode = qr
                )
                showAddStationDialog = false
                Toast.makeText(context, if (stationToEdit == null) "Đã thêm vị trí $name" else "Đã cập nhật vị trí $name", Toast.LENGTH_SHORT).show()
            },
            onDelete = stationToEdit?.let { stationToDelete ->
                {
                    viewModel.deleteShiftStation(stationToDelete)
                    showAddStationDialog = false
                    Toast.makeText(context, "Đã xóa vị trí ${stationToDelete.name}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- DIALOG: Add/Edit Task ---
    if (showAddTaskDialog) {
        val currentStation = selectedStation ?: stations.firstOrNull()
        if (currentStation != null) {
            val editingTask = taskToEdit
            AddShiftTaskDialog(
                stationName = currentStation.name,
                task = editingTask,
                viewModel = viewModel,
                onDismiss = {
                    showAddTaskDialog = false
                    taskToEdit = null
                },
                onSave = { time, title, instruction, priority, reqConf, hasLoudAlarm, isHidden, actionType, targetPackageName, targetAppName ->
                    viewModel.saveShiftTask(
                        context = context,
                        stationId = currentStation.id,
                        taskId = editingTask?.id,
                        timeScheduled = time,
                        title = title,
                        instruction = instruction,
                        priority = priority,
                        requiresConfirmation = reqConf,
                        isCompleted = editingTask?.isCompleted ?: false,
                        completedAt = editingTask?.completedAt,
                        hasLoudAlarm = hasLoudAlarm,
                        isHidden = isHidden,
                        actionType = actionType,
                        targetPackageName = targetPackageName,
                        targetAppName = targetAppName
                    )
                    showAddTaskDialog = false
                    taskToEdit = null
                    Toast.makeText(context, if (editingTask != null) "Đã cập nhật công việc!" else "Đã lưu công việc!", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showAddTaskDialog = false },
                containerColor = DarkCard,
                title = { Text("Chưa có vị trí làm việc", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("Vui lòng thêm một vị trí hoặc máy làm việc trước khi tạo công việc.", color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            showAddTaskDialog = false
                            showAddStationDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShiftBlue)
                    ) {
                        Text("Thêm vị trí mới")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTaskDialog = false }) {
                        Text("Đóng", color = TextSecondary)
                    }
                }
            )
        }
    }

    // --- DIALOG: AI Schedule Planner ---
    if (showAiPlannerDialog) {
        AiSchedulePlannerDialog(
            viewModel = viewModel,
            currentStation = selectedStation,
            onDismiss = { showAiPlannerDialog = false },
            onScheduleAppliedAndStarted = {
                showAiPlannerDialog = false
            }
        )
    }

    // --- DIALOG: Shift Rotation Cycle Configuration ---
    if (showRotationDialog) {
        ShiftRotationDialog(
            viewModel = viewModel,
            onDismiss = { showRotationDialog = false }
        )
    }

    // --- DIALOG: Start Shift Time Alignment Prompt ---
    if (showStartShiftAlignDialog) {
        selectedStation?.let { currentSt ->
            val firstTask = tasks.firstOrNull()
            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val nowStr = timeFormat.format(Calendar.getInstance().time)
            AlertDialog(
                onDismissRequest = { showStartShiftAlignDialog = false },
                containerColor = DarkCard,
                icon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = ShiftLightBlue) },
                title = { Text("Căn chỉnh thời gian bắt đầu", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Mốc công việc đầu tiên (${firstTask?.timeScheduled}) đã trôi qua so với giờ hiện tại ($nowStr).",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            "Bạn muốn giữ nguyên mốc giờ đã cài đặt hay tự động tịnh tiến các công việc bắt đầu từ BÂY GIỜ ($nowStr)?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ShiftLightBlue
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            checkAndRequestNotificationPermission()
                            viewModel.startShiftForStation(currentSt.id, context = context, resetCompleted = true)
                            showStartShiftAlignDialog = false
                            Toast.makeText(context, "Đã bắt đầu ca cho ${currentSt.name} với các mốc giờ đã định!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShiftBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Giữ mốc cũ & Bắt đầu", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.rescheduleStationTasks(context, currentSt.id, nowStr, resetCompleted = true)
                            checkAndRequestNotificationPermission()
                            viewModel.startShiftForStation(currentSt.id, context = context, resetCompleted = true)
                            showStartShiftAlignDialog = false
                            Toast.makeText(context, "Đã căn chỉnh bắt đầu từ $nowStr và BẮT ĐẦU CA!", Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bắt đầu từ BÂY GIỜ ($nowStr)", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            )
        }
    }

    if (showRescheduleDialog) {
        selectedStation?.let { currentSt ->
            RescheduleShiftDialog(
                station = currentSt,
                tasks = tasks,
                onDismiss = { showRescheduleDialog = false },
                onConfirmReschedule = { newStart, resetCompleted ->
                    viewModel.rescheduleStationTasks(
                        context = context,
                        stationId = currentSt.id,
                        newStartTimeStr = newStart,
                        resetCompleted = resetCompleted
                    )
                    showRescheduleDialog = false
                    Toast.makeText(
                        context,
                        "Đã căn chỉnh lại mốc giờ bắt đầu từ $newStart và cập nhật chuông báo!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }
}

// ==================================================
// DANH SÁCH CÔNG VIỆC: MỖI CÔNG VIỆC LÀ MỘT CARD RIÊNG
// ==================================================
@Composable
fun ShiftTaskItemRow(
    task: ShiftTask,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleLoudAlarm: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isHidden) DarkCard.copy(alpha = 0.6f) else DarkCard
        ),
        border = BorderStroke(
            1.dp,
            if (!task.isHidden && task.priority == "Cao") Color(0xFFEF4444).copy(alpha = 0.5f)
            else DarkBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Hàng 1: Giờ trong badge xanh + Tên công việc + Icon sửa/xóa tách biệt góc phải
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Giờ nằm trong một badge màu xanh
                Surface(
                    color = if (task.isHidden) DarkSubCard else ShiftBlue,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = task.timeScheduled,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (task.isHidden) TextSecondary else Color.White
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Tên công việc lớn, dễ đọc, tự xuống dòng nếu dài
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (task.isHidden) TextSecondary else TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                // Nút sửa và xóa nhỏ ở góc trên để không ảnh hưởng nút Ẩn/Hiện
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Chỉnh sửa công việc",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Xóa công việc",
                        tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hàng 2: Priority nằm dưới tên, Trạng thái Ẩn/Hiện, Chuông nằm bên phải
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Priority chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (task.priority) {
                            "Cao" -> Color(0xFF7F1D1D).copy(alpha = 0.35f)
                            "Thấp" -> DarkSubCard
                            else -> Color(0xFF1E3A5F).copy(alpha = 0.4f)
                        },
                        border = BorderStroke(
                            1.dp,
                            when (task.priority) {
                                "Cao" -> Color(0xFFEF4444).copy(alpha = 0.5f)
                                "Thấp" -> DarkBorder
                                else -> Color(0xFF38BDF8).copy(alpha = 0.3f)
                            }
                        )
                    ) {
                        Text(
                            text = task.priority,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (task.priority) {
                                "Cao" -> Color(0xFFFCA5A5)
                                "Thấp" -> TextSecondary
                                else -> Color(0xFF93C5FD)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (task.isHidden) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkSubCard,
                            border = BorderStroke(1.dp, DarkBorder)
                        ) {
                            Text(
                                text = "🔕 Đã ẩn",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Chuông badge nằm bên phải
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (task.hasLoudAlarm) Color(0xFF7F1D1D).copy(alpha = 0.35f) else DarkSubCard,
                    border = BorderStroke(1.dp, if (task.hasLoudAlarm) Color(0xFFEF4444).copy(alpha = 0.5f) else DarkBorder),
                    modifier = Modifier.clickable { onToggleLoudAlarm() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            if (task.hasLoudAlarm) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (task.hasLoudAlarm) Color(0xFFFCA5A5) else TextSecondary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (task.hasLoudAlarm) "Chuông to" else "Chuông êm",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (task.hasLoudAlarm) Color(0xFFFCA5A5) else TextSecondary
                        )
                    }
                }
            }

            // Hiển thị badge Tác vụ tự động hóa (Mở app, Khóa máy, Âm thanh, Bluetooth, Wi-Fi...)
            if (task.actionType.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    val icon = when (task.actionType) {
                        "OPEN_APP" -> "📱"
                        "LOCK_SCREEN" -> "🌙"
                        "SILENT_MODE" -> "🔇"
                        "VIBRATE_MODE" -> "📳"
                        "NORMAL_SOUND" -> "🔔"
                        "BLUETOOTH_ON" -> "🔵"
                        "BLUETOOTH_OFF" -> "🔵"
                        "WIFI_PANEL" -> "📶"
                        "MOBILE_DATA_PANEL" -> "🌐"
                        else -> "⚡"
                    }
                    val label = when (task.actionType) {
                        "OPEN_APP" -> "Tự mở app: ${task.targetAppName.ifBlank { "Ứng dụng" }}"
                        "LOCK_SCREEN" -> "Khóa màn hình & Tắt app (Đi ngủ)"
                        "SILENT_MODE" -> "Tắt âm thanh (Chế độ Im lặng)"
                        "VIBRATE_MODE" -> "Chuyển sang chế độ Rung"
                        "NORMAL_SOUND" -> "Bật lại chuông bình thường"
                        "BLUETOOTH_ON" -> "Bật kết nối Bluetooth"
                        "BLUETOOTH_OFF" -> "Tắt kết nối Bluetooth"
                        "WIFI_PANEL" -> "Bật/Tắt kết nối Wi-Fi"
                        "MOBILE_DATA_PANEL" -> "Mạng di động 4G/5G"
                        else -> "Tác vụ tự động"
                    }
                    val bg = when (task.actionType) {
                        "OPEN_APP" -> Color(0xFF312E81).copy(alpha = 0.5f)
                        "LOCK_SCREEN" -> Color(0xFF451A03).copy(alpha = 0.5f)
                        "SILENT_MODE" -> Color(0xFF3F3F46).copy(alpha = 0.6f)
                        "VIBRATE_MODE" -> Color(0xFF78350F).copy(alpha = 0.45f)
                        "NORMAL_SOUND" -> Color(0xFF064E3B).copy(alpha = 0.45f)
                        "BLUETOOTH_ON" -> Color(0xFF1E3A8A).copy(alpha = 0.45f)
                        "BLUETOOTH_OFF" -> Color(0xFF1E293B).copy(alpha = 0.6f)
                        "WIFI_PANEL" -> Color(0xFF0F766E).copy(alpha = 0.45f)
                        "MOBILE_DATA_PANEL" -> Color(0xFF0369A1).copy(alpha = 0.45f)
                        else -> DarkSubCard
                    }
                    val border = when (task.actionType) {
                        "OPEN_APP" -> Color(0xFF6366F1).copy(alpha = 0.6f)
                        "LOCK_SCREEN" -> Color(0xFFF59E0B).copy(alpha = 0.6f)
                        "SILENT_MODE" -> Color(0xFFA1A1AA).copy(alpha = 0.5f)
                        "VIBRATE_MODE" -> Color(0xFFD97706).copy(alpha = 0.6f)
                        "NORMAL_SOUND" -> Color(0xFF10B981).copy(alpha = 0.6f)
                        "BLUETOOTH_ON" -> Color(0xFF3B82F6).copy(alpha = 0.6f)
                        "BLUETOOTH_OFF" -> Color(0xFF64748B).copy(alpha = 0.6f)
                        "WIFI_PANEL" -> Color(0xFF14B8A6).copy(alpha = 0.6f)
                        "MOBILE_DATA_PANEL" -> Color(0xFF0EA5E9).copy(alpha = 0.6f)
                        else -> DarkBorder
                    }
                    val textCol = when (task.actionType) {
                        "OPEN_APP" -> Color(0xFFA5B4FC)
                        "LOCK_SCREEN" -> Color(0xFFFDE68A)
                        "SILENT_MODE" -> Color(0xFFE4E4E7)
                        "VIBRATE_MODE" -> Color(0xFFFDE68A)
                        "NORMAL_SOUND" -> Color(0xFF6EE7B7)
                        "BLUETOOTH_ON" -> Color(0xFF93C5FD)
                        "BLUETOOTH_OFF" -> Color(0xFFCBD5E1)
                        "WIFI_PANEL" -> Color(0xFF99F6E4)
                        "MOBILE_DATA_PANEL" -> Color(0xFFBAE6FD)
                        else -> TextPrimary
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = bg,
                        border = BorderStroke(1.dp, border)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(icon, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textCol,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Hàng 3: Instruction nằm bên dưới (nếu có), tự tăng chiều cao theo nội dung
            if (task.instruction.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSubCard,
                    border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📖 ${task.instruction}",
                        fontSize = 12.sp,
                        color = TextPrimary.copy(alpha = 0.9f),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hàng 4: Nút Ẩn / Hiện ở phía dưới
            Button(
                onClick = onToggleHidden,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (task.isHidden) DarkSubCard else ShiftBlue,
                    contentColor = if (task.isHidden) TextSecondary else Color.White
                ),
                border = BorderStroke(
                    1.dp,
                    if (task.isHidden) DarkBorder else ShiftBlue.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Icon(
                    imageVector = if (task.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (task.isHidden) TextSecondary else Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (task.isHidden) "👁‍🗨 Đang ẩn (Tắt thông báo) • Nhấn để Hiện" else "🔔 Đang hiện (Bật thông báo) • Nhấn để Ẩn",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==================================================
// DIALOGS: ĐỒNG BỘ DARK THEME
// ==================================================

@Composable
fun AddStationDialog(
    station: ShiftStation? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(station?.name ?: "") }
    var icon by remember { mutableStateOf(station?.icon ?: "🏭") }
    var desc by remember { mutableStateOf(station?.description ?: "") }
    var qr by remember { mutableStateOf(station?.qrCode ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val iconOptions = listOf("🏭", "🔧", "📦", "🧹", "📋", "⚡", "⚙️", "🚚")

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkCard,
            title = { Text("Xác nhận xóa vị trí", color = TextPrimary) },
            text = { Text("Bạn có chắc muốn xóa vị trí \"${station?.name}\"?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Xóa vị trí")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Hủy", color = TextSecondary) }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = if (station == null) "Thêm vị trí / Máy mới" else "Chỉnh sửa vị trí / Máy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên vị trí (Ví dụ: Máy C)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Chọn biểu tượng:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    items(iconOptions) { item ->
                        FilterChip(
                            selected = icon == item,
                            onClick = { icon = item },
                            label = { Text(item, fontSize = 16.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Mô tả ngắn") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (station != null && onDelete != null) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xóa", fontSize = 13.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row {
                        TextButton(onClick = onDismiss) { Text("Hủy", color = TextSecondary) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onSave(name.trim(), icon, desc.trim(), qr.trim())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ShiftBlue)
                        ) {
                            Text("Lưu vị trí")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddShiftTaskDialog(
    stationName: String,
    task: ShiftTask?,
    viewModel: NotesViewModel,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean, Boolean, Boolean, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var time by remember(task?.id) { mutableStateOf(task?.timeScheduled ?: "08:00") }
    var title by remember(task?.id) { mutableStateOf(task?.title ?: "") }
    var instruction by remember(task?.id) { mutableStateOf(task?.instruction ?: "") }
    var priority by remember(task?.id) { mutableStateOf(task?.priority ?: "Bình thường") }
    var hasLoudAlarm by remember(task?.id) { mutableStateOf(task?.hasLoudAlarm ?: false) }
    var isHidden by remember(task?.id) { mutableStateOf(task?.isHidden ?: false) }

    // Tác vụ tự động hóa
    var actionType by remember(task?.id) { mutableStateOf(task?.actionType ?: "") }
    var targetPackageName by remember(task?.id) { mutableStateOf(task?.targetPackageName ?: "") }
    var targetAppName by remember(task?.id) { mutableStateOf(task?.targetAppName ?: "") }
    var showAppPickerDialog by remember { mutableStateOf(false) }

    var isAdminActive by remember { mutableStateOf(viewModel.isDeviceAdminActive(context)) }
    var canOverlay by remember { mutableStateOf(viewModel.canDrawOverlays(context)) }
    var isDndGranted by remember { mutableStateOf(viewModel.isNotificationPolicyAccessGranted(context)) }

    fun openTimePickerDialog() {
        val parsed = time.split(":").mapNotNull { it.trim().toIntOrNull() }
        val curHour = if (parsed.size >= 2) parsed[0] else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val curMinute = if (parsed.size >= 2) parsed[1] else Calendar.getInstance().get(Calendar.MINUTE)

        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
            },
            curHour,
            curMinute,
            true
        ).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (task == null) "Thêm việc cho $stationName" else "Sửa việc $stationName",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Thời gian (Ví dụ: 08:00, 22:15)") },
                    trailingIcon = {
                        IconButton(onClick = { openTimePickerDialog() }) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Chọn giờ", tint = ShiftLightBlue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 2) }
                            time = String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                        },
                        label = { Text("+2 phút", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
                            time = String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                        },
                        label = { Text("+5 phút", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { openTimePickerDialog() },
                        label = { Text("🕒 Chọn giờ", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- KHU VỰC: TỰ ĐỘNG HÓA KHI ĐẾN GIỜ ---
                Text(
                    "⚡ Tác vụ tự động khi đến giờ:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ShiftLightBlue
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Nhóm 1: Cơ bản & Ứng dụng
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = actionType == "",
                        onClick = { actionType = "" },
                        label = { Text("Chỉ nhắc việc", fontSize = 11.sp, maxLines = 1) }
                    )
                    FilterChip(
                        selected = actionType == "OPEN_APP",
                        onClick = {
                            actionType = "OPEN_APP"
                            if (targetPackageName.isBlank()) {
                                showAppPickerDialog = true
                            }
                        },
                        label = { Text("📱 Hẹn mở app", fontSize = 11.sp, maxLines = 1) }
                    )
                    FilterChip(
                        selected = actionType == "LOCK_SCREEN",
                        onClick = {
                            actionType = "LOCK_SCREEN"
                            if (title.isBlank()) {
                                title = "Khóa màn hình & Tắt app (Đi ngủ)"
                            }
                        },
                        label = { Text("🌙 Khóa máy (Đi ngủ)", fontSize = 11.sp, maxLines = 1) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "🔊 Chế độ Âm thanh theo giờ (ví dụ: 22:00 tắt âm thanh, 07:00 bật lại):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFDE68A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = actionType == "SILENT_MODE",
                        onClick = {
                            actionType = "SILENT_MODE"
                            if (title.isBlank()) {
                                title = "Tắt âm thanh (Chế độ Im lặng lúc $time)"
                            }
                        },
                        label = { Text("🔇 Tắt âm thanh (Im lặng)", fontSize = 11.sp, maxLines = 1) }
                    )
                    FilterChip(
                        selected = actionType == "VIBRATE_MODE",
                        onClick = {
                            actionType = "VIBRATE_MODE"
                            if (title.isBlank()) {
                                title = "Chuyển sang chế độ Rung lúc $time"
                            }
                        },
                        label = { Text("📳 Bật rung", fontSize = 11.sp, maxLines = 1) }
                    )
                    FilterChip(
                        selected = actionType == "NORMAL_SOUND",
                        onClick = {
                            actionType = "NORMAL_SOUND"
                            if (title.isBlank()) {
                                title = "Bật lại âm thanh chuông lúc $time"
                            }
                        },
                        label = { Text("🔔 Bật chuông bình thường", fontSize = 11.sp, maxLines = 1) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "📡 Kết nối mạng & Bluetooth:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF93C5FD)
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = actionType == "BLUETOOTH_ON",
                        onClick = {
                            actionType = "BLUETOOTH_ON"
                            if (title.isBlank()) {
                                title = "Bật Bluetooth lúc $time"
                            }
                        },
                        label = { Text("🔵 Bật Bluetooth", fontSize = 11.sp, maxLines = 1) }
                    )
                    FilterChip(
                        selected = actionType == "BLUETOOTH_OFF",
                        onClick = {
                            actionType = "BLUETOOTH_OFF"
                            if (title.isBlank()) {
                                title = "Tắt Bluetooth lúc $time"
                            }
                        },
                        label = { Text("🔵 Tắt Bluetooth", fontSize = 11.sp, maxLines = 1) }
                    )
                    FilterChip(
                        selected = actionType == "WIFI_PANEL",
                        onClick = {
                            actionType = "WIFI_PANEL"
                            if (title.isBlank()) {
                                title = "Bật/Tắt Wi-Fi lúc $time"
                            }
                        },
                        label = { Text("📶 Bật/Tắt Wi-Fi", fontSize = 11.sp, maxLines = 1) }
                    )
                    FilterChip(
                        selected = actionType == "MOBILE_DATA_PANEL",
                        onClick = {
                            actionType = "MOBILE_DATA_PANEL"
                            if (title.isBlank()) {
                                title = "Bật/Tắt Mạng 4G/5G lúc $time"
                            }
                        },
                        label = { Text("🌐 Mạng 4G/5G", fontSize = 11.sp, maxLines = 1) }
                    )
                }

                // Chi tiết cho Hẹn mở app
                if (actionType == "OPEN_APP") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF312E81).copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (targetAppName.isNotBlank()) "📱 Ứng dụng: $targetAppName" else "⚠️ Chưa chọn ứng dụng cần mở",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (targetAppName.isNotBlank()) Color(0xFFA5B4FC) else Color(0xFFFCA5A5)
                                    )
                                    if (targetPackageName.isNotBlank()) {
                                        Text(
                                            text = targetPackageName,
                                            fontSize = 10.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Button(
                                    onClick = { showAppPickerDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(if (targetAppName.isBlank()) "Chọn app" else "Đổi app", fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "💡 Khi ca làm đang BẮT ĐẦU và đến $time, điện thoại sẽ tự động mở ứng dụng này.",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )

                            if (!canOverlay) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(viewModel.getOverlayPermissionIntent(context))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Không thể mở cài đặt", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                                ) {
                                    Text("Cấp quyền 'Hiển thị trên app khác' (Mở tự động)", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Chi tiết cho Khóa máy & Đi ngủ
                if (actionType == "LOCK_SCREEN") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF451A03).copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "🌙 Tự động đóng app & Khóa màn hình lúc $time",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFFDE68A)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hệ thống sẽ tự động thu nhỏ các ứng dụng đang mở (TikTok, Game...) về Home Screen và tắt màn hình để bạn ngủ ngon.",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )

                            val isEmulatorEnv = Build.FINGERPRINT.startsWith("generic") ||
                                    Build.MODEL.contains("google_sdk") ||
                                    Build.MODEL.contains("Emulator") ||
                                    Build.HARDWARE.contains("goldfish") ||
                                    Build.HARDWARE.contains("ranchu")

                            if (isEmulatorEnv) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1E3A5F).copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text("🖥️", fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Chế độ Giả lập AI Studio: Khi đến giờ sẽ tự động thu nhỏ app về Home Screen mà không tắt màn hình đen để bạn dễ dàng test. Trên điện thoại thật của bạn sẽ khóa tắt màn hình hoàn toàn!",
                                            fontSize = 10.sp,
                                            color = Color(0xFFBAE6FD),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            if (isAdminActive) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF065F46).copy(alpha = 0.3f),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Đã cấp quyền Quản trị viên - Màn hình sẽ tự động khóa tức thì",
                                            fontSize = 11.sp,
                                            color = Color(0xFF6EE7B7)
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(viewModel.getDeviceAdminPromptIntent(context))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Không thể mở cài đặt Quản trị viên", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Kích hoạt quyền Quản trị viên (Khóa tức thì)", fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // Chi tiết cho Chế độ Âm thanh (Tắt âm thanh / Rung / Bật chuông)
                if (actionType in listOf("SILENT_MODE", "VIBRATE_MODE", "NORMAL_SOUND")) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color(0xFFEAB308).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = when (actionType) {
                                    "SILENT_MODE" -> "🔇 Tự động TẮT ÂM THANH (Im lặng) lúc $time"
                                    "VIBRATE_MODE" -> "📳 Tự động CHUYỂN SANG RUNG lúc $time"
                                    else -> "🔔 Tự động BẬT LẠI CHUÔNG BÌNH THƯỜNG lúc $time"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFFDE68A)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (actionType) {
                                    "SILENT_MODE" -> "Đến đúng $time, điện thoại sẽ tự động tắt tiếng chuông và thông báo để bạn tập trung làm việc hoặc đi ngủ yên tĩnh (ví dụ: cài 22:00)."
                                    "VIBRATE_MODE" -> "Đến đúng $time, điện thoại sẽ tự động chuyển sang chế độ chỉ Rung khi có cuộc gọi / thông báo."
                                    else -> "Đến đúng $time, điện thoại sẽ tự động khôi phục chuông reo bình thường để bạn không bỏ lỡ cuộc gọi (ví dụ: cài 07:00 thức dậy)."
                                },
                                fontSize = 10.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )

                            if (!isDndGranted) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(viewModel.getNotificationPolicyAccessIntent())
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Không thể mở cài đặt Không làm phiền", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFDE68A))
                                ) {
                                    Text("Cấp quyền Không làm phiền (Để tự động đổi âm thanh)", fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // Chi tiết cho Bluetooth
                if (actionType == "BLUETOOTH_ON" || actionType == "BLUETOOTH_OFF") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E3A8A).copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (actionType == "BLUETOOTH_ON") "🔵 Tự động BẬT Bluetooth lúc $time" else "🔵 Tự động TẮT Bluetooth lúc $time",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF93C5FD)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (actionType == "BLUETOOTH_ON") {
                                    "Đến đúng $time, điện thoại sẽ tự động kích hoạt Bluetooth để sẵn sàng kết nối tai nghe, đồng hồ hoặc thiết bị ngoại vi."
                                } else {
                                    "Đến đúng $time, điện thoại sẽ tự động tắt Bluetooth giúp tiết kiệm pin và ngắt kết nối an toàn."
                                },
                                fontSize = 10.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("💡", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Lưu ý riêng trên Samsung (One UI): Nếu máy đang kết nối Đồng hồ (Galaxy Watch/Gear S) hoặc bật Dò tìm thiết bị, hệ thống Samsung sẽ chặn ứng dụng tắt ngầm phần cứng. Ứng dụng sẽ tự động mở trang Cài đặt Bluetooth để bạn gạt tắt 1 chạm!",
                                        fontSize = 10.sp,
                                        color = Color(0xFFBAE6FD),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Chi tiết cho Wi-Fi & Mạng di động
                if (actionType == "WIFI_PANEL" || actionType == "MOBILE_DATA_PANEL") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F766E).copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color(0xFF14B8A6).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (actionType == "WIFI_PANEL") "📶 Bật/Tắt Wi-Fi nhanh lúc $time" else "🌐 Bật/Tắt Mạng di động (4G/5G) lúc $time",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF99F6E4)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Đến đúng $time, hệ thống sẽ mở bảng điều khiển nhanh hoặc gửi thông báo có nút bấm 1 chạm để bạn bật/tắt kết nối Internet ngay lập tức.",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên công việc / Hoạt động") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("Hướng dẫn chi tiết (Tùy chọn)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Mức độ ưu tiên:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Bình thường", "Cao", "Thấp").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Trạng thái Ẩn / Hiện thông báo
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isHidden) DarkSubCard else Color(0xFF075D8C).copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, if (isHidden) DarkBorder else ShiftBlue.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isHidden = !isHidden }
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = if (isHidden) TextSecondary else Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isHidden) "👁‍🗨 Đang ẨN (Không phát thông báo)" else "🔔 Đang HIỆN (Phát thông báo khi vào ca)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isHidden) TextSecondary else TextPrimary
                            )
                            Text(
                                if (isHidden) "Công việc sẽ không kêu chuông dù ca làm có bắt đầu hay không" else "Phát thông báo bình thường khi đã bấm Bắt đầu ca",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = !isHidden,
                            onCheckedChange = { isHidden = !it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Chuông to / êm
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasLoudAlarm) Color(0xFF7F1D1D).copy(alpha = 0.4f) else DarkSubCard,
                    border = BorderStroke(1.dp, if (hasLoudAlarm) Color(0xFFEF4444).copy(alpha = 0.5f) else DarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { hasLoudAlarm = !hasLoudAlarm }
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            if (hasLoudAlarm) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = if (hasLoudAlarm) Color(0xFFFCA5A5) else TextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (hasLoudAlarm) "🔔 Bật chuông báo thức to (Kho cá)" else "🔕 Thông báo tiêu chuẩn (Ca làm)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (hasLoudAlarm) Color(0xFFFCA5A5) else TextPrimary
                            )
                            Text(
                                if (hasLoudAlarm) "Phát chuông lớn + rung để không ngủ quên" else "Chỉ tạo thông báo nhẹ như cũ",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = hasLoudAlarm,
                            onCheckedChange = { hasLoudAlarm = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Hủy", color = TextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(time.trim(), title.trim(), instruction.trim(), priority, false, hasLoudAlarm, isHidden, actionType, targetPackageName, targetAppName)
                            } else {
                                Toast.makeText(context, "Vui lòng nhập tên công việc!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShiftBlue)
                    ) {
                        Text("Lưu")
                    }
                }
            }
        }
    }

    if (showAppPickerDialog) {
        AppPickerDialog(
            installedApps = remember { viewModel.getInstalledApps(context) },
            onDismiss = { showAppPickerDialog = false },
            onSelectApp = { app ->
                targetPackageName = app.packageName
                targetAppName = app.appName
                if (title.isBlank() || title.startsWith("Mở ")) {
                    title = "Mở ${app.appName}"
                }
                showAppPickerDialog = false
            }
        )
    }
}

@Composable
fun AppPickerDialog(
    installedApps: List<InstalledAppItem>,
    onDismiss: () -> Unit,
    onSelectApp: (InstalledAppItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val popularKeywords = listOf("TikTok", "YouTube", "Facebook", "Zalo", "Chrome", "Báo", "Clock", "Nhạc")
    val popularApps = remember(installedApps) {
        installedApps.filter { app ->
            popularKeywords.any { kw -> app.appName.contains(kw, ignoreCase = true) }
        }.take(8)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Chọn ứng dụng cần hẹn giờ mở",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Hệ thống sẽ tự động bật ứng dụng này khi đến giờ hẹn trong ca.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tìm ứng dụng (ví dụ: TikTok, YouTube)...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ShiftLightBlue) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (popularApps.isNotEmpty() && searchQuery.isBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Gợi ý phổ biến:", fontSize = 11.sp, color = ShiftLightBlue, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(popularApps) { app ->
                            SuggestionChip(
                                onClick = { onSelectApp(app) },
                                label = { Text(app.appName, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tất cả ứng dụng (${filteredApps.size}):",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filteredApps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Không tìm thấy ứng dụng phù hợp", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(filteredApps) { app ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DarkSubCard,
                                border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectApp(app) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ShiftBlue.copy(alpha = 0.35f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = app.appName.firstOrNull()?.uppercase() ?: "📱",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = app.packageName,
                                            fontSize = 10.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        Icons.Default.Launch,
                                        contentDescription = null,
                                        tint = ShiftLightBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Đóng", color = TextSecondary)
                    }
                }
            }
        }
    }
}

