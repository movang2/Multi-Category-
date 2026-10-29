package com.example.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.ai.AiRoadmapPlan
import com.example.ai.AiRoadmapTaskStep
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRoadmapPlannerDialog(
    viewModel: NotesViewModel,
    initialPrompt: String? = null,
    onDismiss: () -> Unit,
    onRoadmapSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatMessages by viewModel.aiRoadmapChatMessages.collectAsState()
    val isThinking by viewModel.isAiRoadmapThinking.collectAsState()

    val todayCalendar = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var startDateStr by remember { mutableStateOf(sdf.format(todayCalendar.time)) }
    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var selectedAiTab by remember { mutableStateOf(0) } // 0 = Chat AI trong app, 1 = Nhờ AI ngoài & Dán JSON
    var externalPasteJsonInput by remember { mutableStateOf("") }
    var externalImportErrorMessage by remember { mutableStateOf<String?>(null) }
    var externalRoadmapTopic by remember { mutableStateOf("Canh tác ngô lai 90 ngày") }
    var externalRoadmapDays by remember { mutableStateOf("90") }
    var externalRoadmapCategory by remember { mutableStateOf("Nông nghiệp") }
    var externalGeneratedPrompt by remember { mutableStateOf("") }

    // Automatically send initial prompt if passed from preset
    var hasSentInitialPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank() && !hasSentInitialPrompt) {
            hasSentInitialPrompt = true
            viewModel.sendRoadmapChatMessage(initialPrompt, startDateStr)
        }
    }

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    // Auto scroll to bottom when new messages arrive or keyboard opens
    LaunchedEffect(chatMessages.size, isThinking, imeBottom) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            var parent = view.parent
            while (parent != null) {
                if (parent is DialogWindowProvider) {
                    parent.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                    break
                }
                parent = parent.parent
            }
            onDispose {}
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("ai_roadmap_planner_dialog"),
            color = Color(0xFF0F1115)
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF065F46), Color(0xFF047857), Color(0xFF0F766E))
                                )
                            )
                            .statusBarsPadding()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("🌱", fontSize = 18.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Trợ lý Lộ trình Đa tháng AI",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp
                                            )
                                        }
                                        Text(
                                            text = if (selectedAiTab == 0) "Trồng trọt, du lịch, học tập, sự kiện dài ngày" else "Tạo prompt cho ChatGPT, Claude & dán JSON",
                                            color = Color(0xFFA7F3D0),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.clearRoadmapChat() }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Làm mới chat", tint = Color.White)
                                }
                            }
                        }

                        // --- TAB SELECTOR ---
                        TabRow(
                            selectedTabIndex = selectedAiTab,
                            containerColor = Color(0xFF161920),
                            contentColor = Color(0xFF34D399)
                        ) {
                            Tab(
                                selected = selectedAiTab == 0,
                                onClick = { selectedAiTab = 0 },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (selectedAiTab == 0) Color(0xFF34D399) else Color(0xFF9CA3AF)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Chat AI trong App",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (selectedAiTab == 0) Color(0xFF34D399) else Color(0xFF9CA3AF)
                                        )
                                    }
                                }
                            )
                            Tab(
                                selected = selectedAiTab == 1,
                                onClick = { selectedAiTab = 1 },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ContentPaste,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (selectedAiTab == 1) Color(0xFF34D399) else Color(0xFF9CA3AF)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Nhờ AI khác & Dán JSON",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (selectedAiTab == 1) Color(0xFF34D399) else Color(0xFF9CA3AF)
                                        )
                                    }
                                }
                            )
                        }

                        if (selectedAiTab == 0) {
                            // --- START DATE SELECTOR BAR ---
                            Surface(
                                color = Color(0xFF1E222D),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(0.5.dp, Color(0xFF374151))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Ngày bắt đầu kế hoạch:",
                                            color = Color(0xFFD1D5DB),
                                            fontSize = 12.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF065F46),
                                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                                        modifier = Modifier.clickable {
                                            val cal = Calendar.getInstance()
                                            try {
                                                val parsed = sdf.parse(startDateStr)
                                                if (parsed != null) cal.time = parsed
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, dayOfMonth ->
                                                    startDateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                                    Toast.makeText(context, "Ngày bắt đầu: ${formatDateDisplay(startDateStr)}", Toast.LENGTH_SHORT).show()
                                                },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatDateDisplay(startDateStr),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            // --- CHAT INPUT BAR (Placed at the top right below plan start date) ---
                            Surface(
                                color = Color(0xFF161920),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(0.5.dp, Color(0xFF262E3D))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = inputPrompt,
                                        onValueChange = { inputPrompt = it },
                                        placeholder = {
                                            Text(
                                                "Nhập yêu cầu lộ trình (ví dụ: Trồng cà chua 100 ngày...)",
                                                fontSize = 12.sp,
                                                color = Color(0xFF6B7280)
                                            )
                                        },
                                        maxLines = 3,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF10B981),
                                            unfocusedBorderColor = Color(0xFF374151),
                                            focusedContainerColor = Color(0xFF1F2937),
                                            unfocusedContainerColor = Color(0xFF1F2937),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = Color(0xFF34D399)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_ai_roadmap_chat_prompt")
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    IconButton(
                                        onClick = {
                                            val query = inputPrompt.trim()
                                            if (query.isNotBlank() && !isThinking) {
                                                viewModel.sendRoadmapChatMessage(query, startDateStr)
                                                inputPrompt = ""
                                            }
                                        },
                                        enabled = inputPrompt.isNotBlank() && !isThinking,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(if (inputPrompt.isNotBlank() && !isThinking) Color(0xFF10B981) else Color(0xFF374151))
                                            .testTag("btn_send_ai_roadmap_prompt")
                                    ) {
                                        if (isThinking) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Send,
                                                contentDescription = "Gửi",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // --- QUICK INSPIRATION CHIPS ---
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF161920))
                                    .padding(bottom = 8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val suggestions = listOf(
                                    "🌽 Trồng ngô 90 ngày" to "Lập lịch trình 90 ngày canh tác ngô lai từ gieo hạt, bón lót, bón thúc đến thu hoạch.",
                                    "🛵 Phượt Xuyên Việt 1 tháng" to "Lập lịch trình du lịch phượt khắp Việt Nam trong vòng 1 tháng từ Hà Nội vào Cà Mau.",
                                    "🌾 Canh tác lúa 100 ngày" to "Lập kế hoạch chăm sóc lúa nước 100 ngày theo các giai đoạn bón phân đón đòng.",
                                    "📚 Tự học Tiếng Anh 2 tháng" to "Lập lộ trình tự học tiếng Anh giao tiếp và từ vựng trong 60 ngày.",
                                    "🏃 Kế hoạch Marathon 3 tháng" to "Lập lịch trình tập luyện chạy bộ cự ly 21km trong vòng 3 tháng."
                                )

                                items(suggestions) { (label, prompt) ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF1F2937),
                                        border = BorderStroke(1.dp, Color(0xFF374151)),
                                        modifier = Modifier.clickable {
                                            inputPrompt = prompt
                                            viewModel.sendRoadmapChatMessage(prompt, startDateStr)
                                            inputPrompt = ""
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            color = Color(0xFF6EE7B7),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                if (selectedAiTab == 0) {
                    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = navBarsBottom + 32.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(chatMessages) { message ->
                            if (message.isUser) {
                                // User Message Bubble
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp),
                                        color = Color(0xFF065F46),
                                        border = BorderStroke(1.dp, Color(0xFF059669)),
                                        modifier = Modifier.widthIn(max = 300.dp)
                                    ) {
                                        Text(
                                            text = message.text,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                }
                            } else {
                                // Model Message Bubble
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF047857)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("🌱", fontSize = 14.sp)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "AI Lập Lộ Trình",
                                                color = Color(0xFF34D399),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Surface(
                                            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
                                            color = Color(0xFF1E222D),
                                            border = BorderStroke(1.dp, Color(0xFF2D3748)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    text = message.text,
                                                    color = Color(0xFFE5E7EB),
                                                    fontSize = 13.sp,
                                                    lineHeight = 19.sp
                                                )

                                                // If AI returned a structured Roadmap plan, show the plan preview card!
                                                if (message.roadmapPlan != null) {
                                                    Spacer(modifier = Modifier.height(14.dp))
                                                    RoadmapPlanPreviewCard(
                                                        plan = message.roadmapPlan,
                                                        startDateStr = startDateStr,
                                                        onApplyPlan = {
                                                            viewModel.applyAndSaveRoadmap(
                                                                plan = message.roadmapPlan,
                                                                startDateStr = startDateStr,
                                                                context = context
                                                            )
                                                            onRoadmapSaved()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (isThinking) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color(0xFF34D399),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "AI đang phân tích và tính toán các mốc ngày cho kế hoạch...",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    ExternalAiRoadmapPromptAndImportView(
                        viewModel = viewModel,
                        startDateStr = startDateStr,
                        onStartDateChange = { startDateStr = it },
                        padding = paddingValues,
                        pasteJsonInput = externalPasteJsonInput,
                        onPasteJsonChange = {
                            externalPasteJsonInput = it
                            externalImportErrorMessage = null
                        },
                        importErrorMessage = externalImportErrorMessage,
                        onErrorMessageChange = { externalImportErrorMessage = it },
                        topicInput = externalRoadmapTopic,
                        onTopicChange = { externalRoadmapTopic = it },
                        durationDaysInput = externalRoadmapDays,
                        onDurationDaysChange = { externalRoadmapDays = it },
                        categoryInput = externalRoadmapCategory,
                        onCategoryChange = { externalRoadmapCategory = it },
                        generatedPrompt = externalGeneratedPrompt,
                        onGeneratedPromptChange = { externalGeneratedPrompt = it },
                        onImportSuccess = {
                            selectedAiTab = 0
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun RoadmapPlanPreviewCard(
    plan: AiRoadmapPlan,
    startDateStr: String,
    onApplyPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedTasks by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)),
        border = BorderStroke(1.5.dp, Color(0xFF10B981)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF065F46)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(plan.icon, fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF047857).copy(alpha = 0.4f)
                        ) {
                            Text(
                                text = plan.category,
                                color = Color(0xFF6EE7B7),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${plan.totalDays} ngày (${plan.tasks.size} công việc)",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (plan.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = plan.description,
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color(0xFF262E3D), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Milestone preview list (first 3 or all)
            val displayedTasks = if (expandedTasks) plan.tasks else plan.tasks.take(3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                displayedTasks.forEachIndexed { index, step ->
                    PlanStepRow(step = step, startDateStr = startDateStr)
                }
            }

            if (plan.tasks.size > 3) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { expandedTasks = !expandedTasks },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = if (expandedTasks) "Thu gọn danh sách" else "Xem toàn bộ ${plan.tasks.size} mốc công việc",
                        color = Color(0xFF34D399),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (expandedTasks) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Apply Button
            Button(
                onClick = onApplyPlan,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_apply_ai_roadmap_plan")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Áp dụng & Lưu Lộ Trình Vào Máy", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun PlanStepRow(
    step: AiRoadmapTaskStep,
    startDateStr: String,
    modifier: Modifier = Modifier
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    try {
        val parsed = sdf.parse(startDateStr)
        if (parsed != null) cal.time = parsed
    } catch (e: Exception) {
        e.printStackTrace()
    }
    cal.add(Calendar.DAY_OF_YEAR, step.dayOffset)
    val displayDate = formatDateDisplay(sdf.format(cal.time))

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1A202C),
        border = BorderStroke(0.5.dp, Color(0xFF2D3748)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.width(85.dp)
            ) {
                Text(
                    text = "Ngày ${step.dayOffset + 1}",
                    color = Color(0xFF34D399),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = displayDate,
                    color = Color(0xFF9CA3AF),
                    fontSize = 10.sp
                )
                Text(
                    text = step.scheduledTime,
                    color = Color(0xFFD1D5DB),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (step.phase.isNotBlank()) {
                    Text(
                        text = step.phase,
                        color = Color(0xFF6EE7B7),
                        fontSize = 10.sp
                    )
                }
                if (step.instruction.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = step.instruction,
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalAiRoadmapPromptAndImportView(
    viewModel: NotesViewModel,
    startDateStr: String,
    onStartDateChange: (String) -> Unit,
    padding: PaddingValues,
    pasteJsonInput: String,
    onPasteJsonChange: (String) -> Unit,
    importErrorMessage: String?,
    onErrorMessageChange: (String?) -> Unit,
    topicInput: String,
    onTopicChange: (String) -> Unit,
    durationDaysInput: String,
    onDurationDaysChange: (String) -> Unit,
    categoryInput: String,
    onCategoryChange: (String) -> Unit,
    generatedPrompt: String,
    onGeneratedPromptChange: (String) -> Unit,
    onImportSuccess: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick suggestion chips for roadmaps
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            val suggestions = listOf(
                Triple("🌽 Canh tác ngô lai 90 ngày", "90", "Nông nghiệp"),
                Triple("🛵 Phượt Xuyên Việt 30 ngày", "30", "Du lịch"),
                Triple("🌾 Lúa nước năng suất cao 100 ngày", "100", "Nông nghiệp"),
                Triple("📚 Ôn thi IELTS 6.5 90 ngày", "90", "Học tập"),
                Triple("🏃 Luyện chạy Half-Marathon 60 ngày", "60", "Sức khỏe"),
                Triple("🍎 Trồng rau sạch sân thượng 45 ngày", "45", "Nông nghiệp")
            )
            items(suggestions) { (title, days, cat) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1F2937),
                    border = BorderStroke(1.dp, Color(0xFF374151)),
                    modifier = Modifier.clickable {
                        onTopicChange(title)
                        onDurationDaysChange(days)
                        onCategoryChange(cat)
                        onGeneratedPromptChange(
                            viewModel.generateExternalRoadmapPrompt(
                                topic = title,
                                startDate = startDateStr,
                                durationDays = days.toIntOrNull() ?: 30,
                                category = cat
                            )
                        )
                    }
                ) {
                    Text(
                        text = title,
                        color = Color(0xFF6EE7B7),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // --- CARD 1: TẠO PROMPT ĐỂ NHỜ AI KHÁC ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
            border = BorderStroke(1.dp, Color(0xFF262E3D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF047857)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "1. Tạo Prompt để nhờ AI khác",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tạo prompt kèm cấu trúc JSON chuẩn cho ChatGPT, Claude, DeepSeek...",
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = topicInput,
                    onValueChange = onTopicChange,
                    label = { Text("Mục tiêu / Nhu cầu lộ trình", fontSize = 12.sp) },
                    placeholder = { Text("Ví dụ: Canh tác ngô lai 90 ngày, Phượt xuyên Việt...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF374151),
                        focusedContainerColor = Color(0xFF1F2937),
                        unfocusedContainerColor = Color(0xFF1F2937),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF34D399),
                        unfocusedLabelColor = Color(0xFF9CA3AF)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Date picker trigger button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1F2937),
                        border = BorderStroke(1.dp, Color(0xFF374151)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val cal = Calendar.getInstance()
                                try {
                                    val parsed = sdf.parse(startDateStr)
                                    if (parsed != null) cal.time = parsed
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val picked = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                        onStartDateChange(picked)
                                        Toast.makeText(context, "Ngày bắt đầu: ${formatDateDisplay(picked)}", Toast.LENGTH_SHORT).show()
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("Ngày bắt đầu", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatDateDisplay(startDateStr),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Duration days input
                    OutlinedTextField(
                        value = durationDaysInput,
                        onValueChange = onDurationDaysChange,
                        label = { Text("Số ngày", fontSize = 11.sp) },
                        placeholder = { Text("90") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedContainerColor = Color(0xFF1F2937),
                            unfocusedContainerColor = Color(0xFF1F2937),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF34D399),
                            unfocusedLabelColor = Color(0xFF9CA3AF)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category selector
                Text(
                    text = "Danh mục lộ trình:",
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF)
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf("Nông nghiệp", "Du lịch", "Học tập", "Sức khỏe", "Đời sống", "Khác")
                    items(categories) { cat ->
                        val isSelected = categoryInput == cat
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF065F46) else Color(0xFF1F2937),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFF374151)),
                            modifier = Modifier.clickable { onCategoryChange(cat) }
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF6EE7B7) else Color(0xFFD1D5DB),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val days = durationDaysInput.toIntOrNull() ?: 30
                            val prompt = viewModel.generateExternalRoadmapPrompt(
                                topic = topicInput,
                                startDate = startDateStr,
                                durationDays = days,
                                category = categoryInput
                            )
                            onGeneratedPromptChange(prompt)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⚡ Tạo Prompt ngay", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }

                    if (generatedPrompt.isNotBlank()) {
                        FilledTonalButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(generatedPrompt))
                                Toast.makeText(context, "Đã sao chép Prompt vào bộ nhớ tạm! Bạn có thể dán vào ChatGPT / Claude ngay.", Toast.LENGTH_LONG).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF065F46),
                                contentColor = Color(0xFF6EE7B7)
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("📋 Sao chép", fontSize = 12.sp)
                        }
                    }
                }

                // If prompt generated, show preview box
                if (generatedPrompt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFF111827),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF374151)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📄 Văn bản Prompt hoàn chỉnh:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399)
                                )
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(generatedPrompt))
                                        Toast.makeText(context, "Đã sao chép Prompt!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF34D399))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", fontSize = 12.sp, color = Color(0xFF34D399))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = generatedPrompt,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFFD1D5DB),
                                maxLines = 14,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // --- CARD 2: DÁN KẾT QUẢ / JSON TỪ AI KHÁC ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
            border = BorderStroke(1.dp, Color(0xFF262E3D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F766E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "2. Dán kết quả JSON từ AI khác vào đây",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Dán khối JSON (hoặc toàn bộ câu trả lời của AI khác) để tạo lộ trình ngay lập tức",
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text ?: ""
                            if (clipText.isNotBlank()) {
                                onPasteJsonChange(clipText)
                                onErrorMessageChange(null)
                                Toast.makeText(context, "Đã dán nội dung từ bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Bộ nhớ tạm (Clipboard) đang trống!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF34D399))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dán từ bộ nhớ tạm (Paste)", fontSize = 12.sp, color = Color(0xFF34D399))
                    }
                }

                OutlinedTextField(
                    value = pasteJsonInput,
                    onValueChange = {
                        onPasteJsonChange(it)
                        onErrorMessageChange(null)
                    },
                    label = { Text("Dán văn bản hoặc mã JSON của AI vào đây", fontSize = 12.sp) },
                    placeholder = {
                        Text(
                            "Ví dụ dán:\n{\n  \"title\": \"Canh tác ngô lai 90 ngày\",\n  \"totalDays\": 90,\n  \"tasks\": [...]\n}",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF374151),
                        focusedContainerColor = Color(0xFF1F2937),
                        unfocusedContainerColor = Color(0xFF1F2937),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF34D399),
                        unfocusedLabelColor = Color(0xFF9CA3AF)
                    )
                )

                if (importErrorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF7F1D1D).copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = importErrorMessage,
                                fontSize = 12.sp,
                                color = Color(0xFFFEE2E2),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val (success, msg) = viewModel.importExternalAiRoadmap(pasteJsonInput)
                        if (success) {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            onPasteJsonChange("")
                            onErrorMessageChange(null)
                            onImportSuccess()
                        } else {
                            onErrorMessageChange(msg)
                        }
                    },
                    enabled = pasteJsonInput.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669),
                        disabledContainerColor = Color(0xFF374151)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🚀 Nhập lộ trình vào App ngay", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
            }
        }

        // Bottom clearance for navigation bar
        val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Spacer(modifier = Modifier.height(navBarsBottom + 36.dp))
    }
}
