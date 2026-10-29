package com.example.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.Category
import com.example.data.Note
import com.example.data.QuickNote
import com.example.utils.AudioHelper
import com.example.utils.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onExportZip: (java.io.File) -> Unit,
    onRequestIgnoreBattery: () -> Unit,
    modifier: Modifier = Modifier,
    launchNoteId: String? = null,
    isQuickNoteLaunch: Boolean = false,
    onHandledLaunch: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: NotesViewModel = viewModel(factory = NotesViewModel.provideFactory(context))

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val notes by viewModel.filteredNotes.collectAsStateWithLifecycle()
    val tags by viewModel.allTags.collectAsStateWithLifecycle()
    val activeQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeTagFilter by viewModel.filterTag.collectAsStateWithLifecycle()
    val activeSortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf("notes") }

    val allQuickNotes by viewModel.allQuickNotesList.collectAsStateWithLifecycle()
    LaunchedEffect(launchNoteId, isQuickNoteLaunch, allQuickNotes) {
        if (launchNoteId != null) {
            val qn = allQuickNotes.find { it.id == launchNoteId }
            if (isQuickNoteLaunch || qn != null) {
                currentTab = "quick_notes"
                viewModel.setTargetQuickNoteId(launchNoteId)
                onHandledLaunch()
            }
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // --- TTS (Text to Speech) Helper ---
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localeResult = tts?.setLanguage(Locale("vi", "VN"))
                if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                isTtsReady = true
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // --- Voice Audio Helper ---
    val audioHelper = remember { AudioHelper(context) }
    var activePlayingNoteId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            audioHelper.stopPlaying()
            audioHelper.stopRecording()
        }
    }

    // --- Dialouges & States Trigger ---
    var showCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit: Category? by remember { mutableStateOf(null) }

    var showEditNoteDialog by remember { mutableStateOf(false) }
    var noteToEdit: Note? by remember { mutableStateOf(null) }

    var showUnlockDialog by remember { mutableStateOf(false) }
    var noteToUnlock: Note? by remember { mutableStateOf(null) }

    var fullScreenImageUri: String? by remember { mutableStateOf(null) }

    // --- ZIP Backup Import Picker ---
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val imported = StorageUtils.importDataFromZip(context, uri)
                if (imported != null) {
                    viewModel.importBackupFromZip(context, imported)
                    Toast.makeText(context, "Nhập sao lưu ZIP thành công!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Tệp ZIP không đúng cấu trúc sao lưu!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Nhập dữ liệu thất bại: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = currentTab != "notes") {
        currentTab = "notes"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = "Notes Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Danh Mục Ghi Chú",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable categories list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Notes, contentDescription = "Tất cả") },
                            label = { Text("Tất cả ghi chú", fontWeight = FontWeight.SemiBold) },
                            selected = currentTab == "notes" && selectedCategory == null,
                            onClick = {
                                currentTab = "notes"
                                viewModel.selectCategory(null)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("all_notes_nav")
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Lộ trình AI", tint = Color(0xFF34D399)) },
                            label = { Text("Lộ trình & Kế hoạch AI", fontWeight = FontWeight.SemiBold, color = Color(0xFF34D399)) },
                            selected = currentTab == "roadmaps",
                            onClick = {
                                currentTab = "roadmaps"
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("roadmaps_drawer_nav")
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Engineering, contentDescription = "Ca làm", tint = Color(0xFF38BDF8)) },
                            label = { Text("Quản lý ca làm", fontWeight = FontWeight.SemiBold) },
                            selected = currentTab == "shift_manager",
                            onClick = {
                                currentTab = "shift_manager"
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("shift_drawer_nav")
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.FlashOn, contentDescription = "Ghi chú nhanh", tint = Color(0xFFFBBF24)) },
                            label = { Text("Ghi chú nhanh", fontWeight = FontWeight.SemiBold) },
                            selected = currentTab == "quick_notes",
                            onClick = {
                                currentTab = "quick_notes"
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("quick_notes_drawer_nav")
                        )
                    }

                    item {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "DANH MỤC GHI CHÚ",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    items(categories) { category ->
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Outlined.Folder, contentDescription = category.name) },
                            label = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category.name,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row {
                                        IconButton(
                                            onClick = {
                                                categoryToEdit = category
                                                showCategoryDialog = true
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Rename",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteCategory(category)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            selected = selectedCategory?.name == category.name,
                            onClick = {
                                viewModel.selectCategory(category)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Bottom buttons of Drawer: Add, Export, Import
                Button(
                    onClick = {
                        categoryToEdit = null
                        showCategoryDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("add_category_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Thêm danh mục")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Thêm danh mục")
                }

                // Background Battery Exemption Whitelist Helper for Samsung/Oppo/Xiaomi
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Hẹn giờ không hoạt động?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Để chuông thông báo hoạt động chính xác khi đóng app, vui lòng tắt tối ưu Pin chạy nền.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onRequestIgnoreBattery() },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Tắt tối ưu Pin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                Toast.makeText(context, "Đang chuẩn bị gói sao lưu ZIP...", Toast.LENGTH_SHORT).show()
                                val zipFile = viewModel.exportAllDataToZip(context)
                                if (zipFile != null) {
                                    onExportZip(zipFile)
                                } else {
                                    Toast.makeText(context, "Xuất sao lưu thất bại!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Xuất", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xuất ZIP", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch("*/*")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Nhập", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nhập ZIP", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentTab) {
                                "quick_notes" -> "Ghi chú nhanh"
                                "shift_manager" -> "Quản lý ca làm"
                                "roadmaps" -> "Lộ trình & Kế hoạch AI"
                                else -> (selectedCategory?.name ?: "Tất cả ghi chú")
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (currentTab == "notes") {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("drawer_menu_toggle")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        } else {
                            IconButton(
                                onClick = { currentTab = "notes" }
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                            }
                        }
                    },
                    actions = {
                        if (currentTab == "notes" && selectedCategory != null) {
                            IconButton(onClick = { viewModel.selectCategory(null) }) {
                                Icon(Icons.Default.ClearAll, contentDescription = "Clear Selection")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = when (currentTab) {
                            "shift_manager" -> Color(0xFF075D8C)
                            "roadmaps" -> Color(0xFF065F46)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        titleContentColor = when (currentTab) {
                            "shift_manager", "roadmaps" -> Color.White
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        navigationIconContentColor = when (currentTab) {
                            "shift_manager", "roadmaps" -> Color.White
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        actionIconContentColor = when (currentTab) {
                            "shift_manager", "roadmaps" -> Color.White
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                )
            },
            floatingActionButton = {
                if (currentTab == "notes") {
                    FloatingActionButton(
                        onClick = {
                            if (selectedCategory == null && categories.isEmpty()) {
                                Toast.makeText(context, "Vui lòng thêm danh mục trước!", Toast.LENGTH_SHORT).show()
                                categoryToEdit = null
                                showCategoryDialog = true
                            } else {
                                noteToEdit = null
                                showEditNoteDialog = true
                            }
                        },
                        modifier = Modifier.testTag("add_note_fab"),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Thêm ghi chú")
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF17181B),
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = currentTab == "notes",
                        onClick = { currentTab = "notes" },
                        icon = { Icon(Icons.Default.Notes, contentDescription = "Ghi chú") },
                        label = { Text("Ghi chú") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "quick_notes",
                        onClick = { currentTab = "quick_notes" },
                        icon = { Icon(Icons.Default.FlashOn, contentDescription = "Ghi chú nhanh") },
                        label = { Text("Ghi chú nhanh") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "shift_manager",
                        onClick = { currentTab = "shift_manager" },
                        icon = { Icon(Icons.Default.Engineering, contentDescription = "Ca làm việc") },
                        label = { Text("Ca làm") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Color(0xFF075D8C),
                            unselectedIconColor = Color(0xFFAEB4BD),
                            unselectedTextColor = Color(0xFFAEB4BD)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == "roadmaps",
                        onClick = { currentTab = "roadmaps" },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Lộ trình AI") },
                        label = { Text("Lộ trình AI") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Color(0xFF065F46),
                            unselectedIconColor = Color(0xFFAEB4BD),
                            unselectedTextColor = Color(0xFFAEB4BD)
                        )
                    )
                }
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            when (currentTab) {
                "quick_notes" -> {
                    QuickNotesView(viewModel = viewModel, innerPadding = innerPadding)
                }
                "shift_manager" -> {
                    ShiftManagementView(viewModel = viewModel, innerPadding = innerPadding)
                }
                "roadmaps" -> {
                    RoadmapManagementView(viewModel = viewModel, innerPadding = innerPadding)
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                // --- Elegant Dark Categories Horizontal Scroll ---
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Button(
                            onClick = { viewModel.selectCategory(null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedCategory == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedCategory == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Tất cả",
                                fontSize = 14.sp,
                                fontWeight = if (selectedCategory == null) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                    items(categories) { category ->
                        val isSelected = selectedCategory?.name == category.name
                        Button(
                            onClick = { viewModel.selectCategory(category) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = category.name,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }

                // --- Search, Tag Chips, and Sort Dropdown panel ---
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = activeQuery,
                                onValueChange = { viewModel.searchQuery.value = it },
                                placeholder = { Text("Tìm ghi chú hoặc tags...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Tìm") },
                                trailingIcon = {
                                    if (activeQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear Query")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("search_note_input"),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Sort selector icon action
                            var sortMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconToggleButton(
                                    checked = sortMenuExpanded,
                                    onCheckedChange = { sortMenuExpanded = it }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort order options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Mới nhất trước") },
                                        leadingIcon = {
                                            if (activeSortOrder == SortOrder.NEWEST) Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Active"
                                            )
                                        },
                                        onClick = {
                                            viewModel.sortOrder.value = SortOrder.NEWEST
                                            sortMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Cũ nhất trước") },
                                        leadingIcon = {
                                            if (activeSortOrder == SortOrder.OLDEST) Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Active"
                                            )
                                        },
                                        onClick = {
                                            viewModel.sortOrder.value = SortOrder.OLDEST
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // --- Horizontal Tag Chips list for fast tag filtering ---
                        if (tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                item {
                                    InputChip(
                                        selected = activeTagFilter == null,
                                        onClick = { viewModel.filterTag.value = null },
                                        label = { Text("Tất cả tag", fontSize = 12.sp) }
                                    )
                                }
                                items(tags) { tag ->
                                    InputChip(
                                        selected = activeTagFilter == tag,
                                        onClick = { viewModel.filterTag.value = if (activeTagFilter == tag) null else tag },
                                        label = { Text("#$tag", fontSize = 12.sp) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Tag,
                                                contentDescription = "Tag IconPoint",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Notes Section ---
                if (notes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentPaste,
                                contentDescription = "Trống",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Chưa có ghi chú nào",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Nhấp vào nút + bên dưới để viết ghi chú mới.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("notes_list"),
                        contentPadding = PaddingValues(bottom = 80.dp, start = 8.dp, end = 8.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                activePlayingNoteId = activePlayingNoteId,
                                isTtsReady = isTtsReady,
                                onTogglePin = { viewModel.togglePin(note) },
                                onSpeak = { text -> tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NoteTTS") },
                                onClick = {
                                    if (note.isLocked) {
                                        noteToUnlock = note
                                        showUnlockDialog = true
                                    } else {
                                        noteToEdit = note
                                        showEditNoteDialog = true
                                    }
                                },
                                onDelete = { viewModel.deleteNote(context, note) },
                                onImageClick = { fullScreenImageUri = it },
                                onAudioPlayClick = { filePath ->
                                    if (activePlayingNoteId == note.id) {
                                        audioHelper.stopPlaying()
                                        activePlayingNoteId = null
                                    } else {
                                        activePlayingNoteId = note.id
                                        audioHelper.startPlaying(filePath) {
                                            activePlayingNoteId = null
                                        }
                                    }
                                },
                                onRemoveReminder = { viewModel.clearReminder(context, note) }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

    // --- DIALOGS ---

    // 1. ADD / EDIT CATEGORY DIALOG
    if (showCategoryDialog) {
        var catName by remember { mutableStateOf(categoryToEdit?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text(if (categoryToEdit == null) "Thêm danh mục" else "Sửa danh mục") },
            text = {
                OutlinedTextField(
                    value = catName,
                    onValueChange = { catName = it },
                    placeholder = { Text("Tên danh mục...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("category_input_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = catName.trim()
                        if (trimmed.isNotEmpty()) {
                            if (categoryToEdit == null) {
                                viewModel.addCategory(trimmed)
                            } else {
                                viewModel.renameCategory(categoryToEdit!!.name, trimmed)
                            }
                            showCategoryDialog = false
                        }
                    },
                    modifier = Modifier.testTag("category_dialog_confirm")
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // 2. UNLOCK NOTE DIALOG
    if (showUnlockDialog && noteToUnlock != null) {
        var inputPin by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showUnlockDialog = false
                noteToUnlock = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nhập mã khóa")
                }
            },
            text = {
                Column {
                    Text("Ghi chú này được mã hóa bảo vệ. Vui lòng nhập mã PIN để mở khóa:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = {
                            inputPin = it
                            isError = false
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = isError,
                        label = { Text("Mã PIN PIN") },
                        modifier = Modifier.fillMaxWidth().testTag("unlock_pin_input")
                    )
                    if (isError) {
                        Text(
                            "Mã PIN không đúng!",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputPin == noteToUnlock?.passwordHash) {
                            val activeNote = noteToUnlock!!
                            showUnlockDialog = false
                            noteToUnlock = null
                            // Open Edit dialog directly as unlocked view
                            noteToEdit = activeNote
                            showEditNoteDialog = true
                        } else {
                            isError = true
                        }
                    },
                    modifier = Modifier.testTag("unlock_confirm_button")
                ) {
                    Text("Mở khóa")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnlockDialog = false
                    noteToUnlock = null
                }) {
                    Text("Hủy")
                }
            }
        )
    }

    // 3. COMPLETE RICH EDIT / POPUP NOTE DIALOG (Substitutes old Javascript prompt edits)
    if (showEditNoteDialog) {
        val currentCategories = categories
        if (currentCategories.isNotEmpty()) {
            RichNoteEditDialog(
                note = noteToEdit,
                categories = currentCategories,
                defaultCategory = selectedCategory ?: currentCategories.first(),
                audioHelper = audioHelper,
                onDismiss = { showEditNoteDialog = false },
                onSave = { categoryName, text, images, pinned, isLocked, passwordHash, voicePath, audioDurationMs, reminderTime ->
                    viewModel.saveNote(
                        context = context,
                        id = noteToEdit?.id,
                        categoryName = categoryName,
                        text = text,
                        images = images,
                        pinned = pinned,
                        isLocked = isLocked,
                        passwordHash = passwordHash,
                        voicePath = voicePath,
                        audioDurationMs = audioDurationMs,
                        reminderTime = reminderTime
                    )
                    showEditNoteDialog = false
                }
            )
        }
    }

    // 4. FULL SCREEN IMAGE VIEWER
    if (fullScreenImageUri != null) {
        Dialog(
            onDismissRequest = { fullScreenImageUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = fullScreenImageUri,
                    contentDescription = "Full Screen Display",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { fullScreenImageUri = null },
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fullScreenImageUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    activePlayingNoteId: String?,
    isTtsReady: Boolean,
    onSpeak: (String) -> Unit,
    onTogglePin: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: (String) -> Unit,
    onAudioPlayClick: (String) -> Unit,
    onRemoveReminder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onTogglePin
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.pinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = if (note.pinned) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.pinned) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Category & Pin indicator & Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Category",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = note.categoryName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isLocked) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(onClick = onTogglePin, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin Note",
                            tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (note.isLocked) {
                // Locked note layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "Lock icon indicator",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ghi chú đã khóa. Nhấn để mở khóa.",
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                // Clear note text content
                Text(
                    text = note.text,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )

                // Render voice attachment play button if available
                if (note.voicePath != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            IconButton(
                                onClick = { onAudioPlayClick(note.voicePath) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (activePlayingNoteId == note.id) Icons.Default.StopCircle else Icons.Default.PlayCircle,
                                    contentDescription = "Play voice memo",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ghi âm đính kèm (${formatDuration(note.audioDurationMs)})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1.0f)
                            )
                            if (activePlayingNoteId == note.id) {
                                LinearProgressIndicator(
                                    modifier = Modifier.width(48.dp).height(4.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Scrolling horizontal lists of images in note
                if (note.images.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(note.images) { imgLink ->
                            AsyncImage(
                                model = imgLink,
                                contentDescription = "Attached Thumbnail Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onImageClick(imgLink) }
                            )
                        }
                    }
                }

                // Scrolling horizontal representation of tag badges
                if (note.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        note.tags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

            // Bottom actions pane: Date & Alarm indicators & Read aloud button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(note.updatedAt)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    // Alarm scheduling feedback in main card
                    if (note.reminderTime != null) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clickable { onRemoveReminder() }
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = "Alarm trigger",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(note.reminderTime)),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove alarm notification",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                // TTS Read Aloud capability
                if (isTtsReady && !note.isLocked && note.text.isNotEmpty()) {
                    IconButton(
                        onClick = { onSpeak(note.text) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Read note text aloud",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// FORMAT TIME HELPER FOR VOICES
fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

// ----------------- RICH NOTE POPUP DIALOG EDITOR -----------------
@Composable
fun RichNoteEditDialog(
    note: Note?,
    categories: List<Category>,
    defaultCategory: Category,
    audioHelper: AudioHelper,
    onDismiss: () -> Unit,
    onSave: (
        categoryName: String,
        text: String,
        images: List<String>,
        pinned: Boolean,
        isLocked: Boolean,
        passwordHash: String?,
        voicePath: String?,
        audioDurationMs: Long,
        reminderTime: Long?
    ) -> Unit
) {
    val context = LocalContext.current

    var noteText by remember { mutableStateOf(note?.text ?: "") }
    var selectedCategoryName by remember { mutableStateOf(note?.categoryName ?: defaultCategory.name) }
    var isPinned by remember { mutableStateOf(note?.pinned ?: false) }

    // Images logic inside popup editor
    val attachedImages = remember { mutableStateListOf<String>().apply { if (note != null) addAll(note.images) } }

    // Alarm reminder logic inside popup editor
    var reminderTimestamp by remember { mutableStateOf(note?.reminderTime) }

    // Lock parameters
    var isLockedState by remember { mutableStateOf(note?.isLocked ?: false) }
    var lockPIN by remember { mutableStateOf(note?.passwordHash ?: "") }

    // Voice memo parameters
    var currentVoicePath by remember { mutableStateOf(note?.voicePath) }
    var currentVoiceDuration by remember { mutableStateOf(note?.audioDurationMs ?: 0L) }
    var isVoiceRecording by remember { mutableStateOf(false) }
    var recordStartTime by remember { mutableStateOf(0L) }

    // --- Media picker launcher for photos ---
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = StorageUtils.copyUriToInternalStorage(context, uri, "note_img", "jpg")
            if (savedPath != null) {
                attachedImages.add(savedPath)
            }
        }
    }

    // --- Speech to Text launcher for native dictation overlay ---
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenTextList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val textSpeech = spokenTextList?.firstOrNull() ?: ""
            if (textSpeech.isNotEmpty()) {
                noteText = if (noteText.isEmpty()) textSpeech else "$noteText $textSpeech"
            }
        }
    }

    // --- Audio Voice MIC recording permissions launcher ---
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission ok, toggle active recording
            val file = audioHelper.startRecording()
            if (file != null) {
                currentVoicePath = file.absolutePath
                recordStartTime = System.currentTimeMillis()
                isVoiceRecording = true
                Toast.makeText(context, "Bắt đầu ghi âm memo...", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Cần cấp quyền Microphone để ghi âm giọng nói!", Toast.LENGTH_LONG).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .systemBarsPadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Row of Popup editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("rich_note_cancel")) {
                        Icon(Icons.Default.Close, contentDescription = "Close Note Editor")
                    }

                    Text(
                        text = if (note == null) "Tạo Ghi Chú" else "Sửa Ghi Chú",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )

                    Button(
                        onClick = {
                            val txt = noteText.trim()
                            if (txt.isEmpty()) {
                                Toast.makeText(context, "Nội dung không thể để trống!", Toast.LENGTH_SHORT).show()
                            } else {
                                if (isLockedState && lockPIN.trim().isEmpty()) {
                                    Toast.makeText(context, "Vui lòng nhập mã khóa PIN!", Toast.LENGTH_SHORT).show()
                                } else {
                                    onSave(
                                        selectedCategoryName,
                                        txt,
                                        attachedImages.toList(),
                                        isPinned,
                                        isLockedState,
                                        if (isLockedState) lockPIN.trim() else null,
                                        currentVoicePath,
                                        currentVoiceDuration,
                                        reminderTimestamp
                                    )
                                }
                            }
                        },
                        modifier = Modifier.testTag("rich_note_save"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Lưu", fontWeight = FontWeight.Bold)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Scrollable fields
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Category selector dropdown
                    item {
                        var catSelectorExpanded by remember { mutableStateOf(false) }
                        Text("Thuộc danh mục:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            OutlinedButton(
                                onClick = { catSelectorExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedCategoryName)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            }
                            DropdownMenu(
                                expanded = catSelectorExpanded,
                                onDismissRequest = { catSelectorExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name) },
                                        onClick = {
                                            selectedCategoryName = cat.name
                                            catSelectorExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Main write area textarea
                    item {
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = { Text("Hãy viết nội dung hoặc tag vào đây (ví dụ: Học tiếng Hàn #học_tiếng_hàn)...") },
                            minLines = 6,
                            maxLines = 14,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rich_note_textarea"),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Dictation Dictation & Voice memo record control Panel
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Ghi âm & Đọc chính tả:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Button 1: Dictation (Speech to Text) uses devices voice recognition UI
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Đang nghe... Hãy nói để nhập liệu")
                                                }
                                                speechRecognizerLauncher.launch(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Thiết bị không hỗ trợ Speech-to-Text!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Dictation Mic", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Chuyển giọng nói", fontSize = 11.sp)
                                    }

                                    // Button 2: Voice Memo Memo (stores raw m4a)
                                    Button(
                                        onClick = {
                                            if (isVoiceRecording) {
                                                // Stop active recording
                                                audioHelper.stopRecording()
                                                currentVoiceDuration = System.currentTimeMillis() - recordStartTime
                                                isVoiceRecording = false
                                                Toast.makeText(context, "Ghi âm đính kèm thành công!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                // Ask for permission and launch recording
                                                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isVoiceRecording) Color.Red else MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isVoiceRecording) Icons.Default.StopCircle else Icons.Default.KeyboardVoice,
                                            contentDescription = "Voice memo record toggle",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isVoiceRecording) "Dừng lại" else "Ghi lưu âm", fontSize = 11.sp)
                                    }
                                }

                                // Visual feedback if voice memo is recorded
                                if (currentVoicePath != null && !isVoiceRecording) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = "Available Audio", tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ghi âm (${formatDuration(currentVoiceDuration)})", fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                // Delete attached recording references
                                                currentVoicePath = null
                                                currentVoiceDuration = 0L
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Trash Audio", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Images collection segment
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Hình ảnh có trong ghi chú:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                TextButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.testTag("add_image_button")
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Photo pick")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🎨 Thêm ảnh")
                                }
                            }

                            if (attachedImages.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Chưa có ảnh nào được thêm vào ghi chú này.",
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    items(attachedImages) { itemPath ->
                                        Box(modifier = Modifier.size(80.dp)) {
                                            AsyncImage(
                                                model = itemPath,
                                                contentDescription = "Attached thumbnail",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.LightGray)
                                            )
                                            IconButton(
                                                onClick = { attachedImages.remove(itemPath) },
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .align(Alignment.TopEnd)
                                                    .background(Color.Red, RoundedCornerShape(50))
                                                    .testTag("delete_attached_image_${attachedImages.indexOf(itemPath)}")
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove photo",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pin toggle
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPinned = !isPinned },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isPinned, onCheckedChange = { isPinned = it })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ghim ghi chú lên đầu trang", fontSize = 14.sp)
                        }
                    }

                    // --- Reminder / Time schedule item ---
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Thời gian nhắc nhở:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    if (reminderTimestamp != null) {
                                        TextButton(onClick = { reminderTimestamp = null }) {
                                            Text("Xóa nhắc nhở", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Alarm,
                                        contentDescription = "Clock schedule alarm indicator",
                                        tint = if (reminderTimestamp != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (reminderTimestamp != null) {
                                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(reminderTimestamp!!))
                                        } else {
                                            "Chưa thiết lập nhắc nhở"
                                        },
                                        fontSize = 13.sp,
                                        color = if (reminderTimestamp != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Button triggers date picker and then time picker natively
                                    Button(
                                        onClick = {
                                            val currentCalendar = Calendar.getInstance()
                                            reminderTimestamp?.let { currentCalendar.timeInMillis = it }
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, dayOfMonth ->
                                                    currentCalendar.set(Calendar.YEAR, year)
                                                    currentCalendar.set(Calendar.MONTH, month)
                                                    currentCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                                                    TimePickerDialog(
                                                        context,
                                                        { _, hourOfDay, minute ->
                                                            currentCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                            currentCalendar.set(Calendar.MINUTE, minute)
                                                            currentCalendar.set(Calendar.SECOND, 0)
                                                            reminderTimestamp = currentCalendar.timeInMillis
                                                        },
                                                        currentCalendar.get(Calendar.HOUR_OF_DAY),
                                                        currentCalendar.get(Calendar.MINUTE),
                                                        true
                                                    ).show()
                                                },
                                                currentCalendar.get(Calendar.YEAR),
                                                currentCalendar.get(Calendar.MONTH),
                                                currentCalendar.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Đặt giờ")
                                    }
                                }
                            }
                        }
                    }

                    // Security PIN lock configurations
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isLockedState = !isLockedState },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = isLockedState, onCheckedChange = { isLockedState = it })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Security, contentDescription = "Lock", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Khóa bảo mật note này", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (isLockedState) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = lockPIN,
                                        onValueChange = { lockPIN = it },
                                        placeholder = { Text("Đặt mã khóa PIN PIN của bạn (ví dụ: 1234)...") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("pin_set_field")
                                    )
                                }
                            }
                        }
                    }
                }


            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNotesView(
    viewModel: NotesViewModel,
    innerPadding: PaddingValues
) {
    val quickNotes by viewModel.filteredQuickNotes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.quickSearchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.quickSortOrder.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val targetQuickNoteId by viewModel.targetQuickNoteId.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<QuickNote?>(null) }

    LaunchedEffect(targetQuickNoteId, quickNotes) {
        if (targetQuickNoteId != null) {
            val target = quickNotes.find { it.id == targetQuickNoteId }
                ?: viewModel.allQuickNotesList.value.find { it.id == targetQuickNoteId }
            if (target != null) {
                noteToEdit = target
                showEditDialog = true
                Toast.makeText(context, "⚡ Đã mở ghi chú nhanh từ thông báo!", Toast.LENGTH_SHORT).show()
                viewModel.setTargetQuickNoteId(null)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.quickSearchQuery.value = it },
                    placeholder = { Text("Tìm kiếm ghi chú nhanh...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.quickSearchQuery.value = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_notes_search_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sắp xếp theo:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    var showSortMenu by remember { mutableStateOf(false) }
                    
                    Box {
                        AssistChip(
                            onClick = { showSortMenu = true },
                            label = {
                                Text(
                                    text = when (sortOrder) {
                                        QuickSortOrder.CREATED_DESC -> "Mới nhất (Tạo)"
                                        QuickSortOrder.CREATED_ASC -> "Cũ nhất (Tạo)"
                                        QuickSortOrder.UPDATED_DESC -> "Mới nhất (Chỉnh sửa)"
                                        QuickSortOrder.UPDATED_ASC -> "Cũ nhất (Chỉnh sửa)"
                                    }
                                )
                            },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                        )
                        
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mới nhất (Ngày tạo)") },
                                onClick = {
                                    viewModel.quickSortOrder.value = QuickSortOrder.CREATED_DESC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cũ nhất (Ngày tạo)") },
                                onClick = {
                                    viewModel.quickSortOrder.value = QuickSortOrder.CREATED_ASC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mới nhất (Ngày sửa)") },
                                onClick = {
                                    viewModel.quickSortOrder.value = QuickSortOrder.UPDATED_DESC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cũ nhất (Ngày sửa)") },
                                onClick = {
                                    viewModel.quickSortOrder.value = QuickSortOrder.UPDATED_ASC
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (quickNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Notes,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Không tìm thấy ghi chú nhanh nào" else "Chưa có ghi chú nhanh nào",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickNotes) { note ->
                    QuickNoteItemRow(
                        note = note,
                        onTogglePin = { viewModel.togglePinQuickNote(note) },
                        onDelete = { viewModel.deleteQuickNote(context, note) },
                        onClick = {
                            noteToEdit = note
                            showEditDialog = true
                        },
                        onQuickReArmMinutes = { minutes ->
                            val trigger = System.currentTimeMillis() + minutes * 60 * 1000L
                            viewModel.updateQuickNoteReminder(context, note, trigger, true)
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            Toast.makeText(context, "⏰ Đã hẹn thông báo sau $minutes phút (lúc ${sdf.format(Date(trigger))})", Toast.LENGTH_SHORT).show()
                        },
                        onCancelReminder = {
                            viewModel.updateQuickNoteReminder(context, note, null, false)
                            Toast.makeText(context, "Đã hủy thông báo nhắc nhở của ghi chú này", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        FloatingActionButton(
            onClick = {
                noteToEdit = null
                showEditDialog = true
            },
            modifier = Modifier
                .padding(bottom = 96.dp, end = 16.dp)
                .testTag("add_quick_note_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Thêm ghi chú nhanh")
        }
    }

    if (showEditDialog) {
        QuickNoteEditDialog(
            note = noteToEdit,
            onDismiss = { showEditDialog = false },
            onSave = { text, pinned, hasNotif, reminderTimeMs ->
                viewModel.saveQuickNote(
                    context = context,
                    id = noteToEdit?.id,
                    text = text,
                    pinned = pinned,
                    hasNotification = hasNotif,
                    reminderTime = reminderTimeMs
                )
                showEditDialog = false
                if (hasNotif && reminderTimeMs != null) {
                    val sdf = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
                    Toast.makeText(context, "⏰ Đã lưu và hẹn thông báo lúc: ${sdf.format(Date(reminderTimeMs))}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Đã lưu ghi chú nhanh", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun QuickNoteItemRow(
    note: QuickNote,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onQuickReArmMinutes: (Int) -> Unit = {},
    onCancelReminder: () -> Unit = {}
) {
    val isFuture = remember(note.reminderTime, note.isNotificationTriggered) {
        val rem = note.reminderTime ?: 0L
        note.hasNotification && rem > System.currentTimeMillis() && !note.isNotificationTriggered
    }
    val isTriggeredOrPast = remember(note.reminderTime, note.isNotificationTriggered) {
        note.hasNotification && (note.isNotificationTriggered || (note.reminderTime != null && note.reminderTime <= System.currentTimeMillis()))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.pinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = if (note.pinned) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.pinned) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (note.pinned) "📌 Đã ghim" else "⚡ Ghi chú nhanh",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (isFuture) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Có thông báo",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin Note",
                            tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = note.text,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )

            // --- NOTIFICATION BANNER / STATUS ---
            if (isFuture && note.reminderTime != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val sdfTime = remember { SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()) }
                val timeStr = remember(note.reminderTime) { sdfTime.format(Date(note.reminderTime)) }
                val diffMins = remember(note.reminderTime) {
                    val m = ((note.reminderTime - System.currentTimeMillis()) / (60 * 1000L)).toInt().coerceAtLeast(1)
                    if (m < 60) "sau $m phút" else "sau ${m / 60}h ${m % 60}p"
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF064E3B).copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Báo lúc $timeStr ($diffMins)",
                            color = Color(0xFF6EE7B7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onCancelReminder,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hủy thông báo",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            } else if (isTriggeredOrPast && note.reminderTime != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val sdfTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                val timeStr = remember(note.reminderTime) { sdfTime.format(Date(note.reminderTime)) }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Đã báo lúc $timeStr",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        // Quick re-arm buttons
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF065F46),
                            modifier = Modifier.clickable { onQuickReArmMinutes(2) }
                        ) {
                            Text(
                                text = "+2p",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF065F46),
                            modifier = Modifier.clickable { onQuickReArmMinutes(5) }
                        ) {
                            Text(
                                text = "+5p",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF374151),
                            modifier = Modifier.clickable { onClick() }
                        ) {
                            Text(
                                text = "Chỉnh",
                                fontSize = 11.sp,
                                color = Color(0xFFD1D5DB),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val sdf = remember { SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()) }
            val formattedTime = remember(note.updatedAt) { sdf.format(Date(note.updatedAt)) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cập nhật: $formattedTime",
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                if (!note.hasNotification) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { onClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AddAlert,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "+ Thông báo",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickNoteEditDialog(
    note: QuickNote?,
    onDismiss: () -> Unit,
    onSave: (text: String, pinned: Boolean, hasNotification: Boolean, reminderTimeMs: Long?) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(note?.text ?: "") }
    var pinned by remember { mutableStateOf(note?.pinned ?: false) }

    // Notification states
    var hasNotification by remember { mutableStateOf(note?.hasNotification ?: false) }
    var notificationMode by remember { mutableStateOf(0) } // 0 = Cách bao nhiêu phút, 1 = Giờ cụ thể trong ngày
    var minutesOffsetStr by remember { mutableStateOf("2") } // Mặc định 2 phút

    val nowCal = remember { Calendar.getInstance() }
    var selectedHour by remember {
        val h = if (note?.reminderTime != null) {
            val c = Calendar.getInstance().apply { timeInMillis = note.reminderTime }
            c.get(Calendar.HOUR_OF_DAY)
        } else {
            (nowCal.get(Calendar.HOUR_OF_DAY) + 1) % 24
        }
        mutableStateOf(h)
    }
    var selectedMinute by remember {
        val m = if (note?.reminderTime != null) {
            val c = Calendar.getInstance().apply { timeInMillis = note.reminderTime }
            c.get(Calendar.MINUTE)
        } else {
            0
        }
        mutableStateOf(m)
    }
    var selectedDateType by remember { mutableStateOf(0) } // 0 = Hôm nay, 1 = Ngày mai, 2 = Ngày khác
    var customDateCalendar by remember {
        val c = Calendar.getInstance()
        if (note?.reminderTime != null) {
            c.timeInMillis = note.reminderTime
        }
        mutableStateOf(c)
    }

    // Dynamic calculated target trigger timestamp
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (note == null) "Thêm ghi chú nhanh" else "Chỉnh sửa ghi chú nhanh",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Viết nội dung ghi chú nhanh...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("quick_note_input"),
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = pinned,
                        onCheckedChange = { pinned = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Ghim lên đầu", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- PHẦN HẸN GIỜ THÔNG BÁO NHẮC NHỞ ---
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (hasNotification) Color(0xFF064E3B).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (hasNotification) Color(0xFF10B981).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                    tint = if (hasNotification) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Phát thông báo nhắc nhở",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (hasNotification) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (hasNotification) "Đang bật hẹn giờ thông báo" else "Tắt (chỉ lưu ghi chú như cũ)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Switch(
                                checked = hasNotification,
                                onCheckedChange = { hasNotification = it }
                            )
                        }

                        if (hasNotification) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFF10B981).copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(10.dp))

                            // Tab lựa chọn chế độ: Theo số phút vs Giờ trong ngày
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (notificationMode == 0) Color(0xFF065F46) else Color(0xFF1F2937),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (notificationMode == 0) Color(0xFF10B981) else Color(0xFF374151)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { notificationMode = 0 }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⏱️ Sau X phút",
                                            fontSize = 12.sp,
                                            fontWeight = if (notificationMode == 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (notificationMode == 0) Color.White else Color(0xFFD1D5DB)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (notificationMode == 1) Color(0xFF065F46) else Color(0xFF1F2937),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (notificationMode == 1) Color(0xFF10B981) else Color(0xFF374151)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { notificationMode = 1 }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🕒 Giờ cụ thể",
                                            fontSize = 12.sp,
                                            fontWeight = if (notificationMode == 1) FontWeight.Bold else FontWeight.Normal,
                                            color = if (notificationMode == 1) Color.White else Color(0xFFD1D5DB)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (notificationMode == 0) {
                                // CHẾ ĐỘ 1: CÁCH BAO NHIÊU PHÚT
                                Text(
                                    text = "Chọn hoặc nhập số phút đếm lùi:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Quick suggestion chips (e.g. 2 phút, 5 phút, 15 phút, 30 phút, 60 phút)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val quickMinutes = listOf("2", "5", "10", "15", "30", "60", "120")
                                    items(quickMinutes) { m ->
                                        val isSelected = minutesOffsetStr.trim() == m
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) Color(0xFF10B981) else Color(0xFF1F2937),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF34D399) else Color(0xFF374151)),
                                            modifier = Modifier.clickable { minutesOffsetStr = m }
                                        ) {
                                            Text(
                                                text = if (m == "60") "+1 giờ" else if (m == "120") "+2 giờ" else "+$m phút",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = minutesOffsetStr,
                                    onValueChange = { minutesOffsetStr = it.filter { char -> char.isDigit() } },
                                    label = { Text("Số phút sau (tùy nhập)", fontSize = 11.sp) },
                                    placeholder = { Text("VD: 2") },
                                    suffix = { Text("phút", fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            } else {
                                // CHẾ ĐỘ 2: THEO THỜI GIAN NHẤT ĐỊNH TRONG NGÀY
                                Text(
                                    text = "Chọn ngày phát thông báo:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selectedDateType == 0) Color(0xFF065F46) else Color(0xFF1F2937),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedDateType == 0) Color(0xFF10B981) else Color(0xFF374151)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedDateType = 0 }
                                    ) {
                                        Text(
                                            text = "Hôm nay",
                                            fontSize = 11.sp,
                                            fontWeight = if (selectedDateType == 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedDateType == 0) Color.White else Color(0xFFD1D5DB),
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selectedDateType == 1) Color(0xFF065F46) else Color(0xFF1F2937),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedDateType == 1) Color(0xFF10B981) else Color(0xFF374151)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedDateType = 1 }
                                    ) {
                                        Text(
                                            text = "Ngày mai",
                                            fontSize = 11.sp,
                                            fontWeight = if (selectedDateType == 1) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedDateType == 1) Color.White else Color(0xFFD1D5DB),
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selectedDateType == 2) Color(0xFF065F46) else Color(0xFF1F2937),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedDateType == 2) Color(0xFF10B981) else Color(0xFF374151)),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .clickable {
                                                selectedDateType = 2
                                                DatePickerDialog(
                                                    context,
                                                    { _, y, m, d ->
                                                        val c = Calendar.getInstance().apply {
                                                            set(y, m, d)
                                                        }
                                                        customDateCalendar = c
                                                    },
                                                    customDateCalendar.get(Calendar.YEAR),
                                                    customDateCalendar.get(Calendar.MONTH),
                                                    customDateCalendar.get(Calendar.DAY_OF_MONTH)
                                                ).show()
                                            }
                                    ) {
                                        val dateStr = SimpleDateFormat("dd/MM", Locale.getDefault()).format(customDateCalendar.time)
                                        Text(
                                            text = if (selectedDateType == 2) "Ngày: $dateStr" else "Ngày khác...",
                                            fontSize = 11.sp,
                                            fontWeight = if (selectedDateType == 2) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedDateType == 2) Color.White else Color(0xFFD1D5DB),
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Chọn Giờ
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1F2937),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF374151)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            TimePickerDialog(
                                                context,
                                                { _, h, m ->
                                                    selectedHour = h
                                                    selectedMinute = m
                                                },
                                                selectedHour,
                                                selectedMinute,
                                                true
                                            ).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Giờ phát thông báo:", fontSize = 12.sp, color = Color(0xFFD1D5DB))
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF065F46)
                                        ) {
                                            Text(
                                                text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF34D399),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // LIVE PREVIEW BANNER
                            if (calculatedReminderTimeMs != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF064E3B).copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val sdfPreview = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault())
                                    val timePreviewStr = sdfPreview.format(Date(calculatedReminderTimeMs))
                                    val diffPreviewMins = ((calculatedReminderTimeMs - System.currentTimeMillis()) / (60 * 1000L)).toInt().coerceAtLeast(1)
                                    val relativeStr = if (diffPreviewMins < 60) "sau $diffPreviewMins phút nữa" else "sau ${diffPreviewMins / 60}h ${diffPreviewMins % 60}p nữa"

                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Alarm,
                                            contentDescription = null,
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Sẽ báo lúc: $timePreviewStr ($relativeStr)",
                                            fontSize = 11.sp,
                                            color = Color(0xFF6EE7B7),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = text.trim()
                            if (trimmed.isNotEmpty()) {
                                onSave(trimmed, pinned, hasNotification, if (hasNotification) calculatedReminderTimeMs else null)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lưu ghi chú", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
