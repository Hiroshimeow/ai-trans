package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.MainViewModel
import com.example.RecordingStatus
import com.example.TranscriptData
import com.example.TranscriptSegment
import com.example.data.AttachmentEntity
import com.example.data.MessageEntity
import com.example.data.RecordingEntity
import com.example.data.LlmProvider
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.util.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScreenChatWorkspaceApp(viewModel: MainViewModel) {
    val isFloatMode by viewModel.isFloatViewMode.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorString.collectAsStateWithLifecycle()

    MyApplicationTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = isFloatMode,
                transitionSpec = {
                    slideInHorizontally { width -> -width } togetherWith
                            slideOutHorizontally { width -> width }
                },
                label = "FloatingToggle"
            ) { floatViewActive ->
                if (floatViewActive) {
                    FloatSimulationView(viewModel = viewModel)
                } else {
                    WorkspaceMainView(viewModel = viewModel)
                }
            }

            // Centralized Error Dialog
            if (errorMsg != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissError() },
                    title = { Text(text = "Workspace Alert", color = MaterialTheme.colorScheme.error) },
                    text = { Text(text = errorMsg ?: "") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("Acknowledge")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceMainView(viewModel: MainViewModel) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 760

    var showMobileMenuDrawer by remember { mutableStateOf(false) }
    var showMobileDetailsSheet by remember { mutableStateOf(false) }
    var showAddSkillDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var pendingRecordAction by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordAudioGranted) {
            android.widget.Toast.makeText(context, "Quyền ghi âm (Microphone) bị từ chối. Vui lòng cấp quyền trong Cài đặt hệ thống để tiếp tục.", android.widget.Toast.LENGTH_LONG).show()
        } else {
            when (pendingRecordAction) {
                "voice_question" -> viewModel.toggleVoiceQuestionRecording()
                "meeting" -> viewModel.startMeetingRecording()
                else -> viewModel.toggleAudioRecording()
            }
        }
        pendingRecordAction = null
    }

    val triggerRecordingSafely = {
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.toggleAudioRecording()
        } else {
            pendingRecordAction = "normal"
            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(android.Manifest.permission.RECORD_AUDIO)
            }
            permissionLauncher.launch(perms)
        }
    }

    val triggerVoiceQuestionSafely = {
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.toggleVoiceQuestionRecording()
        } else {
            pendingRecordAction = "voice_question"
            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(android.Manifest.permission.RECORD_AUDIO)
            }
            permissionLauncher.launch(perms)
        }
    }

    val triggerMeetingSafely = {
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.startMeetingRecording()
        } else {
            pendingRecordAction = "meeting"
            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(android.Manifest.permission.RECORD_AUDIO)
            }
            permissionLauncher.launch(perms)
        }
    }

    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val sessions by viewModel.allSessions.collectAsStateWithLifecycle()

    val currentSessionTitle = sessions.find { it.id == activeSessionId }?.title ?: "No Active Session"

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Keep drawerState in sync with showMobileMenuDrawer
    LaunchedEffect(showMobileMenuDrawer) {
        if (showMobileMenuDrawer) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }
    LaunchedEffect(drawerState.currentValue) {
        showMobileMenuDrawer = drawerState.isOpen
    }

    if (!isWideScreen) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.width(280.dp)
                ) {
                    SidebarPanel(
                        viewModel = viewModel,
                        onAddCustomSkillClick = {
                            showAddSkillDialog = true
                            showMobileMenuDrawer = false
                        }
                    )
                }
            }
        ) {
            MainScaffoldContent(
                isWideScreen = false,
                currentSessionTitle = currentSessionTitle,
                showSettingsDialog = { showSettingsDialog = true },
                showMobileMenuDrawer = { showMobileMenuDrawer = true },
                showMobileDetailsSheet = { showMobileDetailsSheet = true },
                viewModel = viewModel,
                triggerRecordingSafely = triggerRecordingSafely,
                triggerVoiceQuestionSafely = triggerVoiceQuestionSafely,
                triggerMeetingSafely = triggerMeetingSafely
            )
        }
    } else {
        MainScaffoldContent(
            isWideScreen = true,
            currentSessionTitle = currentSessionTitle,
            showSettingsDialog = { showSettingsDialog = true },
            showMobileMenuDrawer = {},
            showMobileDetailsSheet = {},
            viewModel = viewModel,
            triggerRecordingSafely = triggerRecordingSafely,
            triggerVoiceQuestionSafely = triggerVoiceQuestionSafely,
            triggerMeetingSafely = triggerMeetingSafely
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Custom Skill dialog
    if (showAddSkillDialog) {
        AddCustomSkillDialog(
            onDismiss = { showAddSkillDialog = false },
            onConfirmAdd = { title, prompt ->
                viewModel.addNewSkill(title, prompt)
                showAddSkillDialog = false
            }
        )
    }

    // Mobile sheets
    if (!isWideScreen && showMobileDetailsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMobileDetailsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                DetailsPanel(viewModel = viewModel, onStartMeetingClick = triggerMeetingSafely)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffoldContent(
    isWideScreen: Boolean,
    currentSessionTitle: String,
    showSettingsDialog: () -> Unit,
    showMobileMenuDrawer: () -> Unit,
    showMobileDetailsSheet: () -> Unit,
    viewModel: MainViewModel,
    triggerRecordingSafely: () -> Unit,
    triggerVoiceQuestionSafely: () -> Unit,
    triggerMeetingSafely: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Workspace Core",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = currentSessionTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (!isWideScreen) {
                        IconButton(onClick = showMobileMenuDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "DrawerMenu")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.isFloatViewMode.value = true }) {
                        Icon(
                            Icons.Default.Dashboard,
                            contentDescription = "SwitchToFloat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = showSettingsDialog) {
                        Icon(Icons.Default.Settings, contentDescription = "SettingsBoard")
                    }
                    if (!isWideScreen) {
                        IconButton(onClick = showMobileDetailsSheet) {
                            Icon(Icons.Default.Attachment, contentDescription = "DetailsMenu")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isWideScreen) {
                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, BorderSlate)
                ) {
                    SidebarPanel(
                        viewModel = viewModel,
                        onAddCustomSkillClick = {}
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ChatPanel(
                    viewModel = viewModel,
                    triggerRecordingSafely = triggerRecordingSafely,
                    triggerVoiceQuestionSafely = triggerVoiceQuestionSafely
                )
            }

            if (isWideScreen) {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, BorderSlate)
                ) {
                    DetailsPanel(viewModel = viewModel, onStartMeetingClick = triggerMeetingSafely)
                }
            }
        }
    }
}

// ==========================================
// 1. LEFT SIDEBAR PANEL UI
// ==========================================
@Composable
fun SidebarPanel(
    viewModel: MainViewModel,
    onAddCustomSkillClick: () -> Unit
) {
    val activeId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val sessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val skills by viewModel.allSkills.collectAsStateWithLifecycle()

    var showSessionsCollapse by remember { mutableStateOf(true) }
    var showSkillsCollapse by remember { mutableStateOf(true) }

    var skillToEdit by remember { mutableStateOf<com.example.data.PromptSkillEntity?>(null) }
    var skillToView by remember { mutableStateOf<com.example.data.PromptSkillEntity?>(null) }
    var skillToDelete by remember { mutableStateOf<com.example.data.PromptSkillEntity?>(null) }
    var sessionToDeleteId by remember { mutableStateOf<String?>(null) }
    var expandedSkillId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(GlowCyan, ElectricBlue)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ChatBubble,
                    contentDescription = null,
                    tint = CosmicDark,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Screen Chat",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Divider(color = BorderSlate, thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSessionsCollapse = !showSessionsCollapse },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Folder, 
                    contentDescription = null, 
                    tint = GlowCyan, 
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "CONVERSATIONS", 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = SlateTextSecondary,
                    letterSpacing = 1.sp
                )
            }
            Icon(
                if (showSessionsCollapse) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = SlateTextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }

        AnimatedVisibility(visible = showSessionsCollapse) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = { viewModel.createSession("Session ${sessions.size + 1}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Thread", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (sessions.isEmpty()) {
                    Text(
                        "No sessions created",
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontSize = 12.sp,
                        color = SlateTextSecondary,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    sessions.forEach { s ->
                        val isSelected = s.id == activeId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ElectricBlue.copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) ElectricBlue else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { viewModel.selectSession(s.id) }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = null,
                                    tint = if (isSelected) ElectricBlue else SlateTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = s.title,
                                    fontSize = 13.sp,
                                    color = if (isSelected) ElectricBlue else SlateTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (sessions.size > 1) {
                                IconButton(
                                    onClick = { sessionToDeleteId = s.id },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "DeleteSession",
                                        tint = CoralRed,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Divider(color = BorderSlate, thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSkillsCollapse = !showSkillsCollapse },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star, 
                    contentDescription = null, 
                    tint = SoftOrange, 
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "SYSTEM PROMPT SKILLS", 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = SlateTextSecondary,
                    letterSpacing = 1.sp
                )
            }
            Icon(
                if (showSkillsCollapse) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = SlateTextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }

        AnimatedVisibility(visible = showSkillsCollapse) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddCustomSkillClick,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, BorderSlate)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Add Skill", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.restoreBuiltInSkills() },
                        modifier = Modifier.weight(1.2f),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, BorderSlate)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Reset default", fontSize = 10.sp)
                    }
                }

                skills.forEach { p ->
                    val isSkillActive = p.selected || p.alwaysOn
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSkillActive) ElectricBlue.copy(alpha = 0.12f) else Color.Transparent)
                            .border(width = 1.dp, color = if (isSkillActive) ElectricBlue.copy(alpha = 0.3f) else Color.Transparent, shape = RoundedCornerShape(6.dp))
                            .clickable { viewModel.toggleSkill(p.id, !p.selected) }
                            .padding(vertical = 4.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = p.selected,
                            onCheckedChange = { viewModel.toggleSkill(p.id, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = ElectricBlue,
                                uncheckedColor = BorderSlate
                            ),
                            modifier = Modifier.scale(0.85f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSkillActive) ElectricBlue else Color(0xFF1B1B1F)
                                )
                                if (p.alwaysOn) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = "Always On",
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                            Text(
                                p.content,
                                fontSize = 10.sp,
                                color = SlateTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Box {
                            IconButton(
                                onClick = { expandedSkillId = p.id },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = SlateTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = expandedSkillId == p.id,
                                onDismissRequest = { expandedSkillId = null },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = p.alwaysOn,
                                                onCheckedChange = null,
                                                colors = CheckboxDefaults.colors(checkedColor = ElectricBlue),
                                                modifier = Modifier.scale(0.85f)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Always On", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    },
                                    onClick = {
                                        viewModel.toggleSkillAlwaysOn(p.id, !p.alwaysOn)
                                        expandedSkillId = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("View details", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        skillToView = p
                                        expandedSkillId = null
                                    }
                                )
                                if (!p.builtIn) {
                                    DropdownMenuItem(
                                        text = { Text("Edit skill", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            skillToEdit = p
                                            expandedSkillId = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete skill", fontSize = 12.sp, color = CoralRed) },
                                        onClick = {
                                            skillToDelete = p
                                            expandedSkillId = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    sessionToDeleteId?.let { id ->
        val sessionTitle = sessions.find { it.id == id }?.title ?: "session"
        ConfirmDeleteDialog(
            title = "Delete Conversation",
            message = "Are you sure you want to permanently delete \"$sessionTitle\"? This action cannot be undone.",
            onDismiss = { sessionToDeleteId = null },
            onConfirm = {
                viewModel.deleteSession(id)
                sessionToDeleteId = null
            }
        )
    }

    skillToEdit?.let { skill ->
        EditCustomSkillDialog(
            skill = skill,
            onDismiss = { skillToEdit = null },
            onConfirmEdit = { title, content ->
                viewModel.updateSkill(skill.id, title, content)
                skillToEdit = null
            }
        )
    }

    skillToView?.let { skill ->
        ViewSkillDialog(
            skill = skill,
            onDismiss = { skillToView = null }
        )
    }

    skillToDelete?.let { skill ->
        ConfirmDeleteDialog(
            title = "Delete Skill",
            message = "Are you sure you want to permanently delete \"${skill.title}\"? This action cannot be undone.",
            onDismiss = { skillToDelete = null },
            onConfirm = {
                viewModel.deleteSkill(skill.id)
                skillToDelete = null
            }
        )
    }
}

// ==========================================
// 2. CENTER CHAT PANEL
// ==========================================
@Composable
fun ChatPanel(
    viewModel: MainViewModel,
    triggerRecordingSafely: () -> Unit,
    triggerVoiceQuestionSafely: () -> Unit
) {
    val messages by viewModel.activeSessionMessages.collectAsStateWithLifecycle()
    val attachments by viewModel.activeSessionAttachments.collectAsStateWithLifecycle()
    val composerValue by viewModel.composerText.collectAsStateWithLifecycle()

    val isRecording by viewModel.isRecordingAudio.collectAsStateWithLifecycle()
    val isVoiceQActive by viewModel.isRecordingVoiceQuestion.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.isTranscribingAudio.collectAsStateWithLifecycle()
    val durationMs by viewModel.recordingDurationMs.collectAsStateWithLifecycle()
    val rms by viewModel.recordingRmsAmplitude.collectAsStateWithLifecycle()
    val autoStopped by viewModel.recordAutoStoppedDueToSilence.collectAsStateWithLifecycle()

    val sessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val activeId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val activeSession = sessions.find { it.id == activeId }
    var showSessionConfig by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                c.moveToFirst()
                val displayName = if (nameIndex >= 0) c.getString(nameIndex) else "imported_file"
                val sizeBytes = if (sizeIndex >= 0) c.getLong(sizeIndex) else 1024L
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                viewModel.addAttachmentFromUri(uri, displayName, mimeType, sizeBytes)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        activeSession?.let { session ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = GlowCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Session Model: ${session.modelName ?: "Default"} (${session.apiProvider ?: "Gemini"})",
                        fontSize = 11.sp,
                        color = Color(0xFF1B1B1F)
                    )
                }
                TextButton(
                    onClick = { showSessionConfig = !showSessionConfig },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(if (showSessionConfig) "Hide Config" else "Configure Session", fontSize = 11.sp, color = ElectricBlue)
                }
            }

            if (showSessionConfig) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Session API Provider", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val providers = LlmProvider.fromJsonArrayString(viewModel.settingsManager.providersJson)
                        var providerExpanded by remember { mutableStateOf(false) }
                        val currentSessionProviderId = session.apiProvider ?: "gemini"
                        val matchingProvider = providers.find { it.id == currentSessionProviderId }
                            ?: providers.find { it.id == "gemini" }
                            ?: providers.firstOrNull()
                        
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            OutlinedButton(
                                onClick = { providerExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                                border = BorderStroke(1.dp, BorderSlate),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    matchingProvider?.name ?: "Select Provider",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            
                            DropdownMenu(
                                expanded = providerExpanded,
                                onDismissRequest = { providerExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                providers.forEach { prov ->
                                    DropdownMenuItem(
                                        text = { Text(prov.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            providerExpanded = false
                                            val firstModel = prov.models.firstOrNull() ?: ""
                                            viewModel.updateActiveSessionLlmConfig(
                                                endpointUrl = prov.endpointUrl,
                                                modelName = firstModel,
                                                provider = prov.id,
                                                apiKey = prov.apiKey,
                                                maxTokens = prov.maxTokens
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Model for Session", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))

                        val availableModels = matchingProvider?.models ?: emptyList()
                        var modelExpanded by remember { mutableStateOf(false) }
                        val currentModelName = session.modelName ?: ""

                        if (currentSessionProviderId == "gemini") {
                            var customModelInput by remember(currentModelName) { mutableStateOf(currentModelName) }
                            Column {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { modelExpanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                                        border = BorderStroke(1.dp, BorderSlate),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(currentModelName.ifBlank { "Select Gemini Model" }, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    
                                    DropdownMenu(
                                        expanded = modelExpanded,
                                        onDismissRequest = { modelExpanded = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        availableModels.forEach { m ->
                                            DropdownMenuItem(
                                                text = { Text(m, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) },
                                                onClick = {
                                                    modelExpanded = false
                                                    viewModel.updateActiveSessionLlmConfig(
                                                        endpointUrl = session.endpointUrl ?: "",
                                                        modelName = m,
                                                        provider = "gemini",
                                                        apiKey = session.getDecryptedApiKey() ?: "",
                                                        maxTokens = session.maxTokens ?: 4096
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Or input custom name (for Gemini):", fontSize = 10.sp, color = SlateTextSecondary)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextField(
                                        value = customModelInput,
                                        onValueChange = { customModelInput = it },
                                        placeholder = { Text("e.g. gemini-2.5-flash", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                                        textStyle = TextStyle(fontSize = 11.sp),
                                        colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF3F3FA), unfocusedContainerColor = Color(0xFFF3F3FA))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            viewModel.updateActiveSessionLlmConfig(
                                                endpointUrl = session.endpointUrl ?: "",
                                                modelName = customModelInput,
                                                provider = "gemini",
                                                apiKey = session.getDecryptedApiKey() ?: "",
                                                maxTokens = session.maxTokens ?: 4096
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GlowCyan)
                                    ) {
                                        Text("Set", fontSize = 10.sp, color = CosmicDark)
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                OutlinedButton(
                                    onClick = { modelExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                                    border = BorderStroke(1.dp, BorderSlate),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        currentModelName.ifBlank { "Select Model" },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                
                                DropdownMenu(
                                    expanded = modelExpanded,
                                    onDismissRequest = { modelExpanded = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    availableModels.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                modelExpanded = false
                                                viewModel.updateActiveSessionLlmConfig(
                                                    endpointUrl = session.endpointUrl ?: "",
                                                    modelName = m,
                                                    provider = matchingProvider?.id ?: "",
                                                    apiKey = matchingProvider?.apiKey ?: "",
                                                    maxTokens = matchingProvider?.maxTokens ?: 4096
                                                )
                                            }
                                        )
                                    }
                                    if (availableModels.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No models. Tap Settings to configure.", fontSize = 11.sp, color = Color.Red) },
                                            onClick = { modelExpanded = false }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(0.5.dp, BorderSlate)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Endpoint: ${session.endpointUrl.orEmpty().ifBlank { "NATIVE DEFAULT" }}", fontSize = 10.sp, color = SlateTextSecondary)
                                Text("Max Tokens: ${session.maxTokens ?: 4096}", fontSize = 10.sp, color = SlateTextSecondary)
                                val decryptedKey = session.getDecryptedApiKey()
                                val keyDisplay = if (decryptedKey.isNullOrBlank()) "None (Uses Default)" else "Keys Saved: sk-***" + decryptedKey.takeLast(4)
                                Text("API Key: $keyDisplay", fontSize = 10.sp, color = SlateTextSecondary)
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceSlate.copy(alpha = 0.3f))
                .border(1.dp, BorderSlate.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = GlowCyan.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Workspace Conversation Empty",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B1B1F)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Attach files, select prompt skills, or start recording audio files to analyze. Your chat runtime resolves context adaptively.",
                        fontSize = 12.sp,
                        color = SlateTextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.widthIn(max = 400.dp)
                    )
                }
            } else {
                val scrollState = rememberScrollState()
                LaunchedEffect(messages.size) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    messages.forEach { msg ->
                        ChatBubble(
                            message = msg,
                            onSpeak = { text -> viewModel.speakMessageWithTts(text) },
                            onStopSpeak = { viewModel.stopSpeakingTts() }
                        )
                    }
                }
            }
        }

        val selectedAttachments = attachments.filter { it.status == "selected" }
        if (selectedAttachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Staged:", 
                    fontSize = 10.sp, 
                    color = GlowCyan, 
                    fontWeight = FontWeight.Bold
                )
                selectedAttachments.forEach { attach ->
                    val isAudio = attach.mimeType.startsWith("audio/", ignoreCase = true) ||
                                  attach.displayName.endsWith(".mp3", ignoreCase = true) ||
                                  attach.displayName.endsWith(".wav", ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BorderSlate)
                            .clickable {
                                if (isAudio) {
                                    viewModel.transcribeAttachedFile(attach)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (attach.mimeType.startsWith("image/")) Icons.Default.Image 
                            else if (isAudio) Icons.Default.Mic
                            else Icons.Default.Description,
                            contentDescription = null,
                            tint = GlowCyan,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            attach.displayName,
                            fontSize = 10.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 100.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        if (isAudio) {
                            Text(
                                "STT 🎙️",
                                fontSize = 9.sp,
                                color = GlowCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = ActiveGreen,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isRecording || isTranscribing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.8f,
                            targetValue = 1.3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(if (isRecording) scale else 1f)
                                .clip(CircleShape)
                                .background(if (isRecording) (if (isVoiceQActive) GlowCyan else CoralRed) else SoftOrange)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                if (isVoiceQActive) "Hỏi bằng giọng nói (Voice Question)..."
                                else if (isRecording) "Ghi âm Workspace Live..."
                                else "STT Model Transcribing...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isVoiceQActive) GlowCyan else if (isRecording) CoralRed else SoftOrange
                            )
                            if (isRecording) {
                                val seconds = durationMs / 1000
                                val dec = (durationMs % 1000) / 100
                                Text(
                                    "Thời lượng: ${seconds}.${dec}s  (Sóng RMS: %.4f)".format(Locale.US, rms),
                                    fontSize = 10.sp,
                                    color = SlateTextSecondary
                                )
                                val liveSpeechResultVal by viewModel.liveSpeechResult.collectAsStateWithLifecycle()
                                if (liveSpeechResultVal.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Live trans: $liveSpeechResultVal",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isRecording) {
                        Button(
                            onClick = { 
                                if (isVoiceQActive) {
                                    viewModel.toggleVoiceQuestionRecording()
                                } else {
                                    viewModel.toggleAudioRecording()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isVoiceQActive) GlowCyan else CoralRed),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("STOP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isVoiceQActive) CosmicDark else Color.White)
                        }
                    }
                }
            }
        }

        if (autoStopped) {
            LaunchedEffect(autoStopped) {
                kotlinx.coroutines.delay(3000)
                viewModel.recordAutoStoppedDueToSilence.value = false
            }
            Text(
                "Stopped: silence detected",
                fontSize = 11.sp,
                color = CoralRed,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { pickMediaLauncher.launch(arrayOf("*/*")) }) {
                Icon(
                    Icons.Default.AddCircle, 
                    contentDescription = "AttachDocument",
                    tint = GlowCyan
                )
            }

            IconButton(
                onClick = { triggerRecordingSafely() },
                modifier = Modifier
                    .drawBehind {
                        if (isRecording && !isVoiceQActive) {
                            drawCircle(
                                color = CoralRed.copy(alpha = 0.2f),
                                radius = size.minDimension / 1.5f
                            )
                        }
                    }
            ) {
                Icon(
                    imageVector = if (isRecording && !isVoiceQActive) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "VoiceCapture",
                    tint = if (isRecording && !isVoiceQActive) CoralRed else Color(0xFF44474E)
                )
            }

            IconButton(
                onClick = { triggerVoiceQuestionSafely() },
                modifier = Modifier
                    .drawBehind {
                        if (isRecording && isVoiceQActive) {
                            drawCircle(
                                color = GlowCyan.copy(alpha = 0.25f),
                                radius = size.minDimension / 1.5f
                            )
                        }
                    }
            ) {
                Icon(
                    imageVector = if (isRecording && isVoiceQActive) Icons.Default.MicOff else Icons.Default.RecordVoiceOver,
                    contentDescription = "Hỏi Giọng Nói (Voice Question)",
                    tint = if (isRecording && isVoiceQActive) GlowCyan else Color(0xFF44474E)
                )
            }

            TextField(
                value = composerValue,
                onValueChange = { viewModel.composerText.value = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 120.dp),
                placeholder = { Text("Hỏi Workspace (hoặc ghi âm...)", fontSize = 13.sp, color = Color(0xFF74777F)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3F3FA),
                    unfocusedContainerColor = Color(0xFFF3F3FA),
                    focusedTextColor = Color(0xFF1B1B1F),
                    unfocusedTextColor = Color(0xFF1B1B1F),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(20.dp),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = { viewModel.sendMessage() },
                enabled = composerValue.isNotBlank(),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (composerValue.isNotBlank()) GlowCyan else BorderSlate)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "SubmitPrompt",
                    tint = if (composerValue.isNotBlank()) Color.White else SlateTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: MessageEntity,
    onSpeak: (String) -> Unit,
    onStopSpeak: () -> Unit
) {
    val isUser = message.role == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Icon(
                    if (isUser) Icons.Default.Person else Icons.Default.Android,
                    contentDescription = null,
                    tint = if (isUser) ElectricBlue else GlowCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isUser) "You" else "Assistant Workspace",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) ElectricBlue else GlowCyan
                )

                if (!isUser) {
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = { onSpeak(message.content) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speak Text",
                            tint = ElectricBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onStopSpeak() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Stop Speech",
                            tint = CoralRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isUser) 12.dp else 2.dp,
                            bottomEnd = if (isUser) 2.dp else 12.dp
                        )
                    )
                    .background(
                        if (message.isError) CoralRed.copy(alpha = 0.15f)
                        else if (isUser) Color(0xFFD3E3FD)
                        else SurfaceSlate
                    )
                    .border(
                        width = 1.dp,
                        color = if (message.isError) CoralRed
                        else if (isUser) Color(0xFFB0CFFD)
                        else BorderSlate,
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isUser) 12.dp else 2.dp,
                            bottomEnd = if (isUser) 2.dp else 12.dp
                        )
                    )
                    .padding(10.dp)
            ) {
                Text(
                    text = message.content,
                    fontSize = 13.sp,
                    color = if (message.isError) CoralRed 
                           else if (isUser) Color(0xFF041E49) 
                           else Color(0xFF1B1B1F),
                    fontFamily = if (!isUser) FontFamily.SansSerif else FontFamily.Default
                )
            }
        }
    }
}

// ==========================================
// 3. RIGHT DETAILS PANEL UI
// ==========================================
@Composable
fun DetailsPanel(viewModel: MainViewModel, onStartMeetingClick: () -> Unit) {
    var activeTabIdx by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activeTabIdx,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = GlowCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTabIdx]),
                    color = GlowCyan
                )
            }
        ) {
            Tab(
                selected = activeTabIdx == 0,
                onClick = { activeTabIdx = 0 },
                text = { Text("Attachments", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTabIdx == 1,
                onClick = { activeTabIdx = 1 },
                text = { Text("Record Library", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTabIdx == 2,
                onClick = { activeTabIdx = 2 },
                text = { Text("Meeting 🎙️", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(10.dp)
        ) {
            when (activeTabIdx) {
                0 -> AttachmentsTabContent(viewModel = viewModel)
                1 -> RecordingsTabContent(viewModel = viewModel)
                2 -> MeetingTabContent(viewModel = viewModel, onStartMeetingClick = onStartMeetingClick)
            }
        }
    }
}

