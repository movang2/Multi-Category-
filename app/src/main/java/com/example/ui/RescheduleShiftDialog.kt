package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ShiftStation
import com.example.data.ShiftTask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun RescheduleShiftDialog(
    station: ShiftStation,
    tasks: List<ShiftTask>,
    onDismiss: () -> Unit,
    onConfirmReschedule: (newStartTime: String, resetCompleted: Boolean) -> Unit
) {
    val context = LocalContext.current

    // Current time
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val nowStr = remember { timeFormat.format(Calendar.getInstance().time) }

    var customStartTime by remember { mutableStateOf(nowStr) }
    var resetCompletedTasks by remember { mutableStateOf(true) }

    val firstTaskTime = tasks.firstOrNull()?.timeScheduled ?: nowStr

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("reschedule_shift_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Update,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Căn chỉnh lại thời gian",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "${station.icon} ${station.name} (${tasks.size} bước)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Mốc ban đầu bắt đầu lúc $firstTaskTime. Nếu bạn bắt đầu muộn hơn hoặc chuyển sang giờ khác, hệ thống sẽ tự động tịnh tiến toàn bộ các bước tiếp theo tương ứng.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                // Quick One-Click Button: Start from NOW
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val curNow = timeFormat.format(Calendar.getInstance().time)
                            onConfirmReschedule(curNow, resetCompletedTasks)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bắt đầu từ BÂY GIỜ ($nowStr)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Dời bước 1 thành $nowStr và dịch chuyển các bước sau",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Custom Time Input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Hoặc chọn mốc giờ bắt đầu tùy chỉnh:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = customStartTime,
                        onValueChange = { customStartTime = it },
                        label = { Text("Giờ bắt đầu bước 1 (HH:mm)") },
                        placeholder = { Text("Ví dụ: 12:00 hoặc 13:30") },
                        leadingIcon = {
                            Icon(Icons.Default.AccessTime, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_start_time_input"),
                        singleLine = true
                    )

                    // Quick Chips to add +15m, +30m, etc.
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        val quickAddOptions = listOf(
                            Pair("Bây giờ", 0),
                            Pair("+15 phút", 15),
                            Pair("+30 phút", 30),
                            Pair("+1 giờ", 60),
                            Pair("Tròn giờ tới", -1)
                        )

                        items(quickAddOptions) { (label, minutes) ->
                            SuggestionChip(
                                onClick = {
                                    val cal = Calendar.getInstance()
                                    if (minutes == -1) {
                                        cal.add(Calendar.HOUR_OF_DAY, 1)
                                        cal.set(Calendar.MINUTE, 0)
                                    } else {
                                        cal.add(Calendar.MINUTE, minutes)
                                    }
                                    customStartTime = timeFormat.format(cal.time)
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Checkbox: Reset completion status & reschedule alarms
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { resetCompletedTasks = !resetCompletedTasks }
                        .padding(vertical = 2.dp)
                ) {
                    Checkbox(
                        checked = resetCompletedTasks,
                        onCheckedChange = { resetCompletedTasks = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Đặt lại các bước & kích hoạt lại chuông báo",
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (customStartTime.isNotBlank()) {
                        onConfirmReschedule(customStartTime.trim(), resetCompletedTasks)
                    }
                },
                modifier = Modifier.testTag("confirm_reschedule_button")
            ) {
                Text("Áp dụng mốc giờ mới")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
