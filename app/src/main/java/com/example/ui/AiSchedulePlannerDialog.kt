package com.example.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ai.AiChatMessage
import com.example.ai.AiSchedulePlan
import com.example.ai.AiScheduleStep
import com.example.data.ShiftStation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSchedulePlannerDialog(
    viewModel: NotesViewModel,
    currentStation: ShiftStation?,
    onDismiss: () -> Unit,
    onScheduleAppliedAndStarted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val chatMessages by viewModel.aiChatMessages.collectAsState()
    val isThinking by viewModel.isAiThinking.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    var selectedAiTab by remember { mutableStateOf(0) } // 0 = Chat AI trong app, 1 = Nhờ AI ngoài & Dán JSON
    var externalPasteJsonInput by remember { mutableStateOf("") }
    var externalImportErrorMessage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Step editing state
    var editingStepPlanIndex by remember { mutableStateOf<Pair<Int, AiScheduleStep>?>(null) }
    var showAddStepPlanIndex by remember { mutableStateOf<Int?>(null) }
    var planIndexToReschedule by remember { mutableStateOf<Int?>(null) }

    // Dialog to create new station with plan
    var planToCreateStation by remember { mutableStateOf<AiSchedulePlan?>(null) }
    var newStationCustomName by remember { mutableStateOf("") }

    var imeBottomPx by remember { mutableStateOf(0) }
    var navBarsBottomPx by remember { mutableStateOf(0) }

    // Auto scroll to bottom when new messages arrive or keyboard opens
    LaunchedEffect(chatMessages.size, isThinking, imeBottomPx) {
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
            var dialogWindow: android.view.Window? = null
            while (parent != null) {
                if (parent is DialogWindowProvider) {
                    dialogWindow = parent.window
                    break
                }
                parent = parent.parent
            }

            dialogWindow?.let { win ->
                win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }

            val targetView = dialogWindow?.decorView ?: view

            // 1. Initial snapshot of insets if available
            val rootInsets = ViewCompat.getRootWindowInsets(targetView)
            if (rootInsets != null) {
                val ime = rootInsets.getInsets(WindowInsetsCompat.Type.ime())
                val nav = rootInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val sys = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                if (ime.bottom > 0) imeBottomPx = ime.bottom
                val navBottom = maxOf(nav.bottom, sys.bottom)
                if (navBottom > 0) navBarsBottomPx = navBottom
            }

            // 2. WindowInsets listener for real-time changes
            val insetsListener = androidx.core.view.OnApplyWindowInsetsListener { _, insets ->
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                imeBottomPx = ime.bottom
                val navBottom = maxOf(nav.bottom, sys.bottom)
                if (navBottom > 0) navBarsBottomPx = navBottom
                insets
            }
            ViewCompat.setOnApplyWindowInsetsListener(targetView, insetsListener)
            targetView.requestApplyInsets()

            // 3. Smooth keyboard animation callback
            val animCallback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    imeBottomPx = ime.bottom
                    return insets
                }
            }
            ViewCompat.setWindowInsetsAnimationCallback(targetView, animCallback)

            // 4. Bulletproof DisplayFrame layout listener (works on all Samsung OneUI / Android devices)
            val rect = Rect()
            val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                targetView.getWindowVisibleDisplayFrame(rect)
                val totalHeight = targetView.rootView.height
                val diff = totalHeight - rect.bottom
                if (diff > totalHeight * 0.15) {
                    // Soft keyboard is open
                    if (diff > imeBottomPx) {
                        imeBottomPx = diff
                    }
                } else {
                    if (imeBottomPx > 0 && diff < 80) {
                        imeBottomPx = 0
                    }
                    if (diff in 1..250 && diff > navBarsBottomPx) {
                        navBarsBottomPx = diff
                    }
                }
            }
            targetView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

            onDispose {
                ViewCompat.setOnApplyWindowInsetsListener(targetView, null)
                ViewCompat.setWindowInsetsAnimationCallback(targetView, null)
                targetView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("ai_schedule_planner_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    )
                                )
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
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
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = "Trợ lý Lập lịch AI",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.testTag("ai_planner_title")
                                        )
                                        Text(
                                            text = if (selectedAiTab == 0) "Lập lịch tự động bằng AI trong app" else "Tạo prompt cho ChatGPT, Claude & dán JSON",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            viewModel.clearAiChat()
                                            Toast.makeText(context, "Đã làm mới cuộc trò chuyện", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.testTag("refresh_ai_chat_button")
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Làm mới",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }

                                    IconButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.testTag("close_ai_dialog_button")
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Đóng",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        TabRow(
                            selectedTabIndex = selectedAiTab,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Tab(
                                selected = selectedAiTab == 0,
                                onClick = { selectedAiTab = 0 },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Chat AI trong App", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            )
                            Tab(
                                selected = selectedAiTab == 1,
                                onClick = { selectedAiTab = 1 },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Nhờ AI khác & Dán JSON", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            )
                        }

                        if (selectedAiTab == 0) {
                            // Chat Input field + send button + chips placed at TOP right under "Chat AI trong App"
                            Surface(
                                tonalElevation = 3.dp,
                                shadowElevation = 4.dp,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    // Chat Input field + send button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = inputPrompt,
                                            onValueChange = { inputPrompt = it },
                                            placeholder = {
                                                Text(
                                                    "Nhập yêu cầu: Ví dụ 'Kho cá 5 bước', 'Tuần này làm ca đêm'...",
                                                    fontSize = 13.sp
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("ai_input_textfield"),
                                            maxLines = 3,
                                            shape = RoundedCornerShape(24.dp)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        IconButton(
                                            onClick = {
                                                if (inputPrompt.isNotBlank() && !isThinking) {
                                                    val prompt = inputPrompt
                                                    inputPrompt = ""
                                                    viewModel.sendAiSchedulePrompt(prompt)
                                                }
                                            },
                                            enabled = inputPrompt.isNotBlank() && !isThinking,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (inputPrompt.isNotBlank() && !isThinking) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .testTag("send_ai_prompt_button")
                                        ) {
                                            if (isThinking) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Send,
                                                    contentDescription = "Gửi",
                                                    tint = if (inputPrompt.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Quick suggestion chips
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 2.dp)
                                    ) {
                                        val suggestions = listOf(
                                            "🔄 Tuần này ca ngày, tuần sau ca đêm",
                                            "🐟 Lập lịch kho cá ngon",
                                            "🎯 1 ngày học tập & vui chơi",
                                            "🏭 Ca làm việc 8 tiếng",
                                            "💪 Lịch tập thể thao & nghỉ ngơi"
                                        )

                                        items(suggestions) { text ->
                                            SuggestionChip(
                                                onClick = {
                                                    inputPrompt = text
                                                    viewModel.sendAiSchedulePrompt(text)
                                                    inputPrompt = ""
                                                },
                                                label = { Text(text, fontSize = 12.sp) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                if (selectedAiTab == 0) {
                    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = maxOf(navBarsBottom + 16.dp, 32.dp)),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                    items(chatMessages.size) { index ->
                        val message = chatMessages[index]
                        ChatMessageItem(
                            message = message,
                            currentStation = currentStation,
                            onEditStep = { step ->
                                editingStepPlanIndex = Pair(index, step)
                            },
                            onDeleteStep = { stepId ->
                                val plan = message.plan ?: return@ChatMessageItem
                                val updatedSteps = plan.steps.filter { it.id != stepId }
                                val updatedPlan = plan.copy(steps = updatedSteps)
                                val updatedMsg = message.copy(plan = updatedPlan)
                                val list = chatMessages.toMutableList()
                                list[index] = updatedMsg
                                viewModel.aiChatMessages.value = list
                                Toast.makeText(context, "Đã xóa bước", Toast.LENGTH_SHORT).show()
                            },
                            onAddStep = {
                                showAddStepPlanIndex = index
                            },
                            onShiftStartTime = {
                                planIndexToReschedule = index
                            },
                            onShiftToNow = {
                                val plan = message.plan ?: return@ChatMessageItem
                                val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                val nowStr = timeFormat.format(java.util.Calendar.getInstance().time)
                                val shifted = viewModel.shiftAiPlanSteps(plan.steps, nowStr)
                                val updatedPlan = plan.copy(steps = shifted)
                                val updatedMsg = message.copy(plan = updatedPlan)
                                val list = chatMessages.toMutableList()
                                list[index] = updatedMsg
                                viewModel.aiChatMessages.value = list
                                Toast.makeText(context, "Đã dời mốc bắt đầu từ BÂY GIỜ ($nowStr)!", Toast.LENGTH_SHORT).show()
                            },
                            onToggleStepLoudAlarm = { stepId ->
                                val plan = message.plan ?: return@ChatMessageItem
                                val updatedSteps = plan.steps.map { s ->
                                    if (s.id == stepId) s.copy(hasLoudAlarm = !s.hasLoudAlarm) else s
                                }
                                val updatedPlan = plan.copy(steps = updatedSteps)
                                val updatedMsg = message.copy(plan = updatedPlan)
                                val list = chatMessages.toMutableList()
                                list[index] = updatedMsg
                                viewModel.aiChatMessages.value = list
                            },
                            onToggleAllPlanLoudAlarm = { enableLoud ->
                                val plan = message.plan ?: return@ChatMessageItem
                                val updatedSteps = plan.steps.map { s -> s.copy(hasLoudAlarm = enableLoud) }
                                val updatedPlan = plan.copy(steps = updatedSteps)
                                val updatedMsg = message.copy(plan = updatedPlan)
                                val list = chatMessages.toMutableList()
                                list[index] = updatedMsg
                                viewModel.aiChatMessages.value = list
                                Toast.makeText(
                                    context,
                                    if (enableLoud) "Đã bật chuông báo thức to cho tất cả các bước (thích hợp kho cá/nấu ăn)"
                                    else "Đã chuyển sang thông báo êm",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onApplyToCurrentStation = { startShiftNow ->
                                val plan = message.plan ?: return@ChatMessageItem
                                if (currentStation == null) {
                                    Toast.makeText(context, "Vui lòng chọn hoặc tạo một vị trí làm việc trước", Toast.LENGTH_SHORT).show()
                                    return@ChatMessageItem
                                }
                                viewModel.applyAiScheduleToStation(
                                    context = context,
                                    stationId = currentStation.id,
                                    steps = plan.steps,
                                    startShiftImmediately = startShiftNow
                                )
                                if (startShiftNow) {
                                    Toast.makeText(
                                        context,
                                        "Đã áp dụng và BẮT ĐẦU CA cho ${currentStation.name}! Đã thiết lập chuông báo.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onScheduleAppliedAndStarted()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Đã thêm ${plan.steps.size} công việc vào ${currentStation.name}!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                            },
                            onCreateNewStation = { plan ->
                                planToCreateStation = plan
                                newStationCustomName = plan.suggestedStationName
                            },
                            onApplyRotationPlan = { rotPlan ->
                                viewModel.applyRotationPlanFromAi(context, rotPlan)
                                onDismiss()
                            }
                        )
                    }

                    if (isThinking) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("AI đang tính toán và phân bổ mốc thời gian...", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                    }
                } else {
                    ExternalAiPromptAndImportView(
                        viewModel = viewModel,
                        padding = padding,
                        pasteJsonInput = externalPasteJsonInput,
                        onPasteJsonChange = {
                            externalPasteJsonInput = it
                            externalImportErrorMessage = null
                        },
                        importErrorMessage = externalImportErrorMessage,
                        onErrorMessageChange = { externalImportErrorMessage = it },
                        onImportSuccess = {
                            selectedAiTab = 0
                        }
                    )
                }
            }
        }
    }

    // Dialog for Editing a Step
    editingStepPlanIndex?.let { (msgIdx, step) ->
        EditAiStepDialog(
            initialStep = step,
            onDismiss = { editingStepPlanIndex = null },
            onSave = { updatedStep ->
                val message = chatMessages.getOrNull(msgIdx)
                val plan = message?.plan
                if (plan != null) {
                    val updatedSteps = plan.steps.map { if (it.id == updatedStep.id) updatedStep else it }
                    val updatedPlan = plan.copy(steps = updatedSteps)
                    val updatedMsg = message.copy(plan = updatedPlan)
                    val list = chatMessages.toMutableList()
                    list[msgIdx] = updatedMsg
                    viewModel.aiChatMessages.value = list
                }
                editingStepPlanIndex = null
                Toast.makeText(context, "Đã cập nhật bước thành công", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog for Adding a New Step
    showAddStepPlanIndex?.let { msgIdx ->
        EditAiStepDialog(
            initialStep = AiScheduleStep(
                timeScheduled = "09:00",
                title = "",
                instruction = "",
                priority = "Bình thường"
            ),
            isNewStep = true,
            onDismiss = { showAddStepPlanIndex = null },
            onSave = { newStep ->
                val message = chatMessages.getOrNull(msgIdx)
                val plan = message?.plan
                if (plan != null) {
                    val updatedSteps = plan.steps + newStep
                    val updatedPlan = plan.copy(steps = updatedSteps)
                    val updatedMsg = message.copy(plan = updatedPlan)
                    val list = chatMessages.toMutableList()
                    list[msgIdx] = updatedMsg
                    viewModel.aiChatMessages.value = list
                }
                showAddStepPlanIndex = null
                Toast.makeText(context, "Đã thêm bước mới", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog for Shifting Plan Start Time (e.g. 11h44 to 12h00 or current time)
    planIndexToReschedule?.let { msgIdx ->
        val message = chatMessages.getOrNull(msgIdx)
        val plan = message?.plan
        if (plan != null) {
            val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
            val nowStr = remember { timeFormat.format(java.util.Calendar.getInstance().time) }
            var customStart by remember { mutableStateOf(nowStr) }

            AlertDialog(
                onDismissRequest = { planIndexToReschedule = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Update, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dời mốc giờ bắt đầu", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Các bước tiếp theo sẽ tự động tịnh tiến thời gian theo mốc bắt đầu mới bạn chọn.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Quick Button: Set to NOW
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val shifted = viewModel.shiftAiPlanSteps(plan.steps, nowStr)
                                    val updatedPlan = plan.copy(steps = shifted)
                                    val updatedMsg = message.copy(plan = updatedPlan)
                                    val list = chatMessages.toMutableList()
                                    list[msgIdx] = updatedMsg
                                    viewModel.aiChatMessages.value = list
                                    planIndexToReschedule = null
                                    Toast.makeText(context, "Đã dời bắt đầu từ BÂY GIỜ ($nowStr)!", Toast.LENGTH_SHORT).show()
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Bắt đầu từ BÂY GIỜ ($nowStr)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Bước 1 sẽ bắt đầu vào $nowStr", fontSize = 11.sp)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customStart,
                            onValueChange = { customStart = it },
                            label = { Text("Hoặc nhập giờ tùy ý (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val chips = listOf("+15 phút", "+30 phút", "+1 giờ", "Tròn giờ")
                            items(chips) { chipLabel ->
                                SuggestionChip(
                                    onClick = {
                                        val cal = java.util.Calendar.getInstance()
                                        when (chipLabel) {
                                            "+15 phút" -> cal.add(java.util.Calendar.MINUTE, 15)
                                            "+30 phút" -> cal.add(java.util.Calendar.MINUTE, 30)
                                            "+1 giờ" -> cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
                                            "Tròn giờ" -> {
                                                cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
                                                cal.set(java.util.Calendar.MINUTE, 0)
                                            }
                                        }
                                        customStart = timeFormat.format(cal.time)
                                    },
                                    label = { Text(chipLabel, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customStart.isNotBlank()) {
                                val shifted = viewModel.shiftAiPlanSteps(plan.steps, customStart.trim())
                                val updatedPlan = plan.copy(steps = shifted)
                                val updatedMsg = message.copy(plan = updatedPlan)
                                val list = chatMessages.toMutableList()
                                list[msgIdx] = updatedMsg
                                viewModel.aiChatMessages.value = list
                                planIndexToReschedule = null
                                Toast.makeText(context, "Đã dời bắt đầu từ $customStart!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Áp dụng")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { planIndexToReschedule = null }) {
                        Text("Hủy")
                    }
                }
            )
        }
    }

    // Dialog to create a brand new Station from AI Plan
    planToCreateStation?.let { plan ->
        var startImmediately by remember { mutableStateOf(true) }
        var alignStartToNow by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { planToCreateStation = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plan.suggestedIcon, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tạo vị trí mới từ lịch trình AI", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Đặt tên vị trí/kế hoạch mới:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = newStationCustomName,
                        onValueChange = { newStationCustomName = it },
                        label = { Text("Tên vị trí (ví dụ: Kho cá 🐟)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { startImmediately = !startImmediately }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = startImmediately,
                            onCheckedChange = { startImmediately = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Bắt đầu ca ngay và hẹn giờ chuông báo cho ${plan.steps.size} bước",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { alignStartToNow = !alignStartToNow }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = alignStartToNow,
                            onCheckedChange = { alignStartToNow = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "⚡ Tự động căn chỉnh mốc giờ bắt đầu từ BÂY GIỜ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newStationCustomName.ifBlank { plan.suggestedStationName }
                        val nowStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                        val finalSteps = if (alignStartToNow) viewModel.shiftAiPlanSteps(plan.steps, nowStr) else plan.steps
                        viewModel.createStationAndApplyAiSchedule(
                            context = context,
                            stationName = name,
                            stationIcon = plan.suggestedIcon,
                            steps = finalSteps,
                            startShiftImmediately = startImmediately
                        )
                        planToCreateStation = null
                        if (startImmediately) {
                            Toast.makeText(
                                context,
                                "Đã tạo vị trí '$name' và BẮT ĐẦU CA! Chuông nhắc nhở đã được kích hoạt.",
                                Toast.LENGTH_LONG
                            ).show()
                            onScheduleAppliedAndStarted()
                        } else {
                            Toast.makeText(context, "Đã tạo vị trí '$name' thành công!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                ) {
                    Text("Tạo & Áp dụng")
                }
            },
            dismissButton = {
                TextButton(onClick = { planToCreateStation = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun ChatMessageItem(
    message: AiChatMessage,
    currentStation: ShiftStation?,
    onEditStep: (AiScheduleStep) -> Unit,
    onDeleteStep: (String) -> Unit,
    onAddStep: () -> Unit,
    onShiftStartTime: () -> Unit,
    onShiftToNow: () -> Unit,
    onToggleStepLoudAlarm: (String) -> Unit,
    onToggleAllPlanLoudAlarm: (Boolean) -> Unit,
    onApplyToCurrentStation: (startShiftNow: Boolean) -> Unit,
    onCreateNewStation: (AiSchedulePlan) -> Unit,
    onApplyRotationPlan: ((com.example.ai.AiRotationPlan) -> Unit)? = null
) {
    if (message.isUser) {
        // User bubble
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    } else {
        // AI bubble
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Card(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // If this message contains a proposed schedule plan
            message.plan?.let { plan ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                            )
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(plan.suggestedIcon, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = plan.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${plan.steps.size} bước gợi ý (có thể sửa tự do)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick start time adjustment bar
                        val firstStepTime = plan.steps.firstOrNull()?.timeScheduled ?: "08:00"
                        val anyLoud = plan.steps.any { it.hasLoudAlarm }
                        val allLoud = plan.steps.isNotEmpty() && plan.steps.all { it.hasLoudAlarm }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.AccessTime,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Bắt đầu: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = firstStepTime,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            FilledTonalButton(
                                                onClick = onShiftToNow,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Bắt đầu BÂY GIỜ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = onShiftStartTime,
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp).testTag("shift_plan_time_button")
                                            ) {
                                                Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Dời giờ", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Sound style indicator and toggle for whole plan
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (allLoud) "🔊 Chuông to: Đang bật chuông báo to cho mọi bước"
                                                   else if (anyLoud) "🔔 Đang bật chuông to cho một số bước"
                                                   else "🔕 Thông báo êm (Ca làm)",
                                            fontSize = 11.sp,
                                            color = if (anyLoud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (anyLoud) FontWeight.Medium else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )

                                        TextButton(
                                            onClick = { onToggleAllPlanLoudAlarm(!allLoud) },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text(
                                                text = if (allLoud) "Tắt chuông to" else "Bật chuông to (Kho cá)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (allLoud) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Timeline of steps
                        plan.steps.forEachIndexed { idx, step ->
                            AiStepCard(
                                stepNumber = idx + 1,
                                step = step,
                                onEdit = { onEditStep(step) },
                                onDelete = { onDeleteStep(step.id) },
                                onToggleLoudAlarm = { onToggleStepLoudAlarm(step.id) }
                            )
                            if (idx < plan.steps.size - 1) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Add new step button
                        TextButton(
                            onClick = onAddStep,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Thêm bước thủ công", fontSize = 12.sp)
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        // Action CTA: Chỉ giữ nút Tạo vị trí mới
                        Button(
                            onClick = { onCreateNewStation(plan) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_new_station_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tạo vị trí mới", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // If this message contains a proposed rotation plan
            message.rotationPlan?.let { rotPlan ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔄", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Chu kỳ xoay ca (${rotPlan.cycleWeeks} tuần)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = rotPlan.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Daily switch time badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Tự động đổi ca lúc: ${rotPlan.switchTime} mỗi ngày",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Weeks breakdown
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rotPlan.weeks.forEach { week ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "📅 ${week.weekLabel}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        week.days.forEach { day ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    day.dayName,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (day.stationName.lowercase().contains("nghỉ"))
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                    else MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = day.stationName,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (day.stationName.lowercase().contains("nghỉ"))
                                                            MaterialTheme.colorScheme.onSecondaryContainer
                                                        else MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { onApplyRotationPlan?.invoke(rotPlan) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("apply_rotation_plan_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Áp dụng chu kỳ xoay ca ngay", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiStepCard(
    stepNumber: Int,
    step: AiScheduleStep,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleLoudAlarm: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time Badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = step.timeScheduled,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // Step Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = step.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (step.priority == "Cao") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                "Ưu tiên",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    // Sound style toggle badge on step
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (step.hasLoudAlarm) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onToggleLoudAlarm() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Icon(
                                if (step.hasLoudAlarm) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = if (step.hasLoudAlarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (step.hasLoudAlarm) "Chuông to" else "Chuông êm",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (step.hasLoudAlarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (step.instruction.isNotBlank()) {
                    Text(
                        text = step.instruction,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Edit & Delete icons
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Sửa bước",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Xóa bước",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun EditAiStepDialog(
    initialStep: AiScheduleStep,
    isNewStep: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (AiScheduleStep) -> Unit
) {
    var timeScheduled by remember { mutableStateOf(initialStep.timeScheduled) }
    var title by remember { mutableStateOf(initialStep.title) }
    var instruction by remember { mutableStateOf(initialStep.instruction) }
    var priority by remember { mutableStateOf(initialStep.priority) }
    var hasLoudAlarm by remember { mutableStateOf(initialStep.hasLoudAlarm) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNewStep) "Thêm bước lịch trình mới" else "Chỉnh sửa bước lịch trình",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = timeScheduled,
                    onValueChange = { timeScheduled = it },
                    label = { Text("Mốc giờ (ví dụ: 08:30 hoặc 14:00)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên công việc / bước") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("Hướng dẫn / Lưu ý chi tiết") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Độ ưu tiên:", fontSize = 13.sp)
                    listOf("Bình thường", "Cao").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p, fontSize = 12.sp) }
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasLoudAlarm) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { hasLoudAlarm = !hasLoudAlarm }
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            if (hasLoudAlarm) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = if (hasLoudAlarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (hasLoudAlarm) "🔔 Bật chuông báo thức to (Kho cá)" else "🔕 Thông báo êm (Đi làm)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (hasLoudAlarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (hasLoudAlarm) "Phát chuông lớn + rung để không ngủ quên" else "Chỉ tạo thông báo nhẹ như cũ",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hasLoudAlarm,
                            onCheckedChange = { hasLoudAlarm = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            initialStep.copy(
                                timeScheduled = timeScheduled.trim(),
                                title = title.trim(),
                                instruction = instruction.trim(),
                                priority = priority,
                                hasLoudAlarm = hasLoudAlarm
                            )
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Lưu bước")
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
fun ExternalAiPromptAndImportView(
    viewModel: NotesViewModel,
    padding: PaddingValues,
    pasteJsonInput: String,
    onPasteJsonChange: (String) -> Unit,
    importErrorMessage: String?,
    onErrorMessageChange: (String?) -> Unit,
    onImportSuccess: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var topicInput by remember { mutableStateOf("lịch trình đi chơi 1 ngày ở Đà Nẵng") }
    var startTimeInput by remember { mutableStateOf("07:30") }
    var endTimeInput by remember { mutableStateOf("22:00") }
    var generatedPrompt by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Quick suggestion chips to generate prompts
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            val suggestions = listOf(
                "Đi chơi 1 ngày ở Đà Nẵng",
                "Quy trình kho cá ngon 5 bước",
                "Ca làm việc xưởng máy 8 tiếng",
                "1 ngày học tập và giải trí"
            )
            items(suggestions) { sample ->
                SuggestionChip(
                    onClick = {
                        topicInput = sample
                        if (sample.contains("kho cá", ignoreCase = true)) {
                            startTimeInput = "10:30"
                            endTimeInput = "12:00"
                        } else if (sample.contains("ca làm", ignoreCase = true)) {
                            startTimeInput = "08:00"
                            endTimeInput = "16:30"
                        } else {
                            startTimeInput = "07:30"
                            endTimeInput = "22:00"
                        }
                        generatedPrompt = viewModel.generateExternalPrompt(
                            topic = topicInput,
                            startTime = startTimeInput,
                            endTime = endTimeInput
                        )
                    },
                    label = { Text(sample, fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        // --- CARD 1: TẠO PROMPT NHỜ AI KHÁC ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "1. Tạo Prompt để nhờ AI khác",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Nhập mục tiêu, app sẽ tạo văn bản kèm khối JSON chuẩn để bạn copy sang ChatGPT, Claude...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = topicInput,
                    onValueChange = { topicInput = it },
                    label = { Text("Nhu cầu lịch trình của bạn") },
                    placeholder = { Text("Ví dụ: lịch trình đi chơi 1 ngày ở Đà Nẵng, kho cá...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startTimeInput,
                        onValueChange = { startTimeInput = it },
                        label = { Text("Giờ bắt đầu") },
                        placeholder = { Text("07:30") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = endTimeInput,
                        onValueChange = { endTimeInput = it },
                        label = { Text("Giờ kết thúc") },
                        placeholder = { Text("22:00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            generatedPrompt = viewModel.generateExternalPrompt(
                                topic = topicInput,
                                startTime = startTimeInput.ifBlank { "07:30" },
                                endTime = endTimeInput.ifBlank { "22:00" }
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⚡ Tạo Prompt ngay", fontWeight = FontWeight.Bold)
                    }

                    if (generatedPrompt.isNotBlank()) {
                        FilledTonalButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(generatedPrompt))
                                Toast.makeText(context, "Đã sao chép Prompt vào bộ nhớ tạm! Bạn có thể dán vào ChatGPT / Claude ngay.", Toast.LENGTH_LONG).show()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("📋 Sao chép Prompt")
                        }
                    }
                }

                // If prompt is generated, show preview box
                if (generatedPrompt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(generatedPrompt))
                                        Toast.makeText(context, "Đã sao chép Prompt!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", fontSize = 12.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = generatedPrompt,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 12,
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
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "2. Dán kết quả JSON từ AI khác vào đây",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Dán khối JSON (hoặc toàn bộ câu trả lời của AI khác) để tạo lịch trình ngay lập tức",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dán từ bộ nhớ tạm (Paste)", fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = pasteJsonInput,
                    onValueChange = {
                        onPasteJsonChange(it)
                        onErrorMessageChange(null)
                    },
                    label = { Text("Dán văn bản hoặc mã JSON của AI vào đây") },
                    placeholder = {
                        Text(
                            "Ví dụ dán: {\n  \"scheduleTitle\": \"Lịch trình...\",\n  \"steps\": [...]\n}",
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                if (importErrorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ $importErrorMessage",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val (success, msg) = viewModel.importExternalAiSchedule(pasteJsonInput)
                        if (success) {
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            onPasteJsonChange("")
                            onErrorMessageChange(null)
                            onImportSuccess()
                        } else {
                            onErrorMessageChange(msg)
                        }
                    },
                    enabled = pasteJsonInput.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🚀 Nhập lịch trình vào App ngay", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Extra bottom clearance so all content can be scrolled comfortably above any bottom bar
        Spacer(modifier = Modifier.height(36.dp))
    }
}
