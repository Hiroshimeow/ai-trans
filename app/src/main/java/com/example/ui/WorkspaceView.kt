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
                viewModel = viewModel
            )
        }
    } else {
        MainScaffoldContent(
            isWideScreen = true,
            currentSessionTitle = currentSessionTitle,
            showSettingsDialog = { showSettingsDialog = true },
            showMobileMenuDrawer = {},
            showMobileDetailsSheet = {},
            viewModel = viewModel
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
                DetailsPanel(viewModel = viewModel)
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
    viewModel: MainViewModel
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
                ChatPanel(viewModel = viewModel)
            }

            if (isWideScreen) {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, BorderSlate)
                ) {
                    DetailsPanel(viewModel = viewModel)
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
                                .background(if (isSelected) SurfaceSlate else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) BorderSlate else Color.Transparent,
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
                                    tint = if (isSelected) GlowCyan else SlateTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = s.title,
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color.White else SlateTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (sessions.size > 1) {
                                IconButton(
                                    onClick = { viewModel.deleteSession(s.id) },
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (p.selected) SurfaceSlate else Color.Transparent)
                            .clickable { viewModel.toggleSkill(p.id, !p.selected) }
                            .padding(vertical = 4.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = p.selected,
                            onCheckedChange = { viewModel.toggleSkill(p.id, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = GlowCyan,
                                uncheckedColor = BorderSlate
                            ),
                            modifier = Modifier.scale(0.85f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                p.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (p.selected) Color.White else SlateTextSecondary
                            )
                            Text(
                                p.content,
                                fontSize = 10.sp,
                                color = SlateTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!p.builtIn) {
                            IconButton(
                                onClick = { viewModel.deleteSkill(p.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "DeleteSkill",
                                    tint = CoralRed.copy(alpha = 0.8f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. CENTER CHAT PANEL
// ==========================================
@Composable
fun ChatPanel(viewModel: MainViewModel) {
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
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordAudioGranted) {
            android.widget.Toast.makeText(context, "Quyền ghi âm (Microphone) bị từ chối. Vui lòng cấp quyền trong Cài đặt hệ thống để tiếp tục.", android.widget.Toast.LENGTH_LONG).show()
        } else {
            viewModel.toggleAudioRecording()
        }
    }

    val triggerRecordingSafely = {
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.toggleAudioRecording()
        } else {
            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(android.Manifest.permission.RECORD_AUDIO)
            }
            permissionLauncher.launch(perms)
        }
    }

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
                                modifier = Modifier.background(SurfaceSlate)
                            ) {
                                providers.forEach { prov ->
                                    DropdownMenuItem(
                                        text = { Text(prov.name, fontSize = 11.sp, color = Color(0xFF1B1B1F)) },
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
                                        modifier = Modifier.background(SurfaceSlate)
                                    ) {
                                        availableModels.forEach { m ->
                                            DropdownMenuItem(
                                                text = { Text(m, fontSize = 11.sp, color = Color(0xFF1B1B1F)) },
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
                                        placeholder = { Text("e.g. gemini-3.5-flash", fontSize = 11.sp) },
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
                                    modifier = Modifier.background(SurfaceSlate)
                                ) {
                                    availableModels.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m, fontSize = 11.sp, color = Color(0xFF1B1B1F)) },
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
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24).copy(alpha = 0.5f)),
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
                        "Attach screenshots, select prompt skills, or start recording audio files to analyze. Your chat runtime resolves context adaptively.",
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
                    .background(Color.Black.copy(alpha = 0.4f))
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
                onClick = { viewModel.toggleVoiceQuestionRecording() },
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
fun DetailsPanel(viewModel: MainViewModel) {
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
                2 -> MeetingTabContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MeetingTabContent(viewModel: MainViewModel) {
    val status by viewModel.recordingStatus.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecordingAudio.collectAsStateWithLifecycle()
    val durationMs by viewModel.recordingDurationMs.collectAsStateWithLifecycle()
    val rms by viewModel.recordingRmsAmplitude.collectAsStateWithLifecycle()
    val liveTranscript by viewModel.liveMeetingTranscript.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    LaunchedEffect(liveTranscript) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
            border = BorderStroke(1.dp, BorderSlate)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Workspace Meeting Sync",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Thời lượng: %.1fs".format(Locale.US, durationMs / 1000f),
                        fontSize = 10.sp,
                        color = SlateTextSecondary
                    )
                }

                val badgeColor = when (status) {
                    RecordingStatus.IDLE -> Color(0xFF74777F)
                    RecordingStatus.RECORDING -> CoralRed
                    RecordingStatus.STOPPING -> SoftOrange
                    RecordingStatus.TRANSCRIBING -> GlowCyan
                    RecordingStatus.COMPLETED -> ActiveGreen
                    RecordingStatus.SKIPPED -> Color(0xFF8E9199)
                    RecordingStatus.ERROR -> Color(0xFFBA1A1A)
                    RecordingStatus.TIMEOUT -> Color(0xFFFFB4AB)
                }
                val statusLabel = when (status) {
                    RecordingStatus.IDLE -> "Sẵn sàng"
                    RecordingStatus.RECORDING -> "Đang ghi âm cuộc họp"
                    RecordingStatus.STOPPING -> "Đang dừng"
                    RecordingStatus.TRANSCRIBING -> "Đang STT bằng AI..."
                    RecordingStatus.COMPLETED -> "Đã hoàn thành"
                    RecordingStatus.SKIPPED -> "Đã hủy bỏ"
                    RecordingStatus.ERROR -> "Lỗi hệ thống"
                    RecordingStatus.TIMEOUT -> "Quá thời gian"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
        }

        if (isRecording) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val barCount = 15
                    for (i in 0 until barCount) {
                        val baseHeight = 3.dp
                        val factor = (rms * 120).coerceIn(1f, 32f)
                        val noise = remember(rms) { (4..16).random() }
                        val activeHeight = if (i % 2 == 0) baseHeight + factor.dp else baseHeight + (factor / 2).dp + noise.dp
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(activeHeight.coerceAtMost(30.dp))
                                .clip(RoundedCornerShape(2.dp))
                                .background(GlowCyan)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            if (liveTranscript.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = null,
                            tint = BorderSlate,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Chưa có nội dung cuộc họp.\nHãy bắt đầu ghi âm để chuyển ngữ thô live.",
                            fontSize = 11.sp,
                            color = SlateTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = liveTranscript,
                            fontSize = 12.sp,
                            color = Color.White,
                            lineHeight = 18.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { viewModel.insertTranscriptIntoComposer(liveTranscript) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Input,
                                contentDescription = "InsertToComposer",
                                tint = GlowCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(GlowCyan.copy(alpha = 0.05f))
                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = GlowCyan,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(top = 1.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Cuộc họp được lưu đồng thời xuống file .wav và .txt trong bộ nhớ cache liên tục của Workspace. Nếu có sự cố tắt máy hoặc crash bất ngờ, dữ liệu văn bản họp vẫn đầy đủ.",
                    fontSize = 10.sp,
                    color = SlateTextSecondary,
                    lineHeight = 14.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isRecording) {
                Button(
                    onClick = { viewModel.startMeetingRecording() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BẮT ĐẦU HỌP 🎙️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Button(
                    onClick = { viewModel.stopMeetingRecording() },
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("HOÀN THÀNH HỌP ⏹️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = { viewModel.cancelAudioRecording() },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, CoralRed),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed)
                ) {
                    Text("HỦY ❌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AttachmentsTabContent(viewModel: MainViewModel) {
    val attachments by viewModel.activeSessionAttachments.collectAsStateWithLifecycle()

    if (attachments.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = BorderSlate, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Không có tệp đính kèm", fontSize = 12.sp, color = SlateTextSecondary)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(attachments, key = { it.id }) { item ->
                AttachmentRow(item = item, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AttachmentRow(item: AttachmentEntity, viewModel: MainViewModel) {
    val isInChat = item.status == "already_in_chat"
    val isSelected = item.status == "selected"
    val isAudio = item.mimeType.startsWith("audio/", ignoreCase = true) ||
                  item.displayName.endsWith(".mp3", ignoreCase = true) ||
                  item.displayName.endsWith(".wav", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceSlate)
            .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BorderSlate),
            contentAlignment = Alignment.Center
        ) {
            if (item.mimeType.startsWith("image/")) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (isAudio) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = SoftOrange,
                    modifier = Modifier.size(18.dp)
                )
            } else if (item.displayName.endsWith(".pdf", ignoreCase = true) || item.mimeType.equals("application/pdf", ignoreCase = true)) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = CoralRed,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = GlowCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1B1B1F),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sizeDisplay = "%.1f KB".format(Locale.US, item.sizeBytes / 1024.0)
            Text(
                "$sizeDisplay • ${item.source}",
                fontSize = 10.sp,
                color = SlateTextSecondary
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        if (isAudio) {
            IconButton(
                onClick = { viewModel.transcribeAttachedFile(item) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Transcribe Audio",
                    tint = GlowCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        if (isInChat) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(BorderSlate)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("In Chat", fontSize = 9.sp, color = SlateTextSecondary)
            }
        } else {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { viewModel.toggleAttachmentSelection(item.id, it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = GlowCyan,
                    uncheckedColor = BorderSlate
                ),
                modifier = Modifier.scale(0.85f)
            )
        }

        IconButton(
            onClick = { viewModel.removeAttachment(item.id) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "RemoveAttachment", tint = CoralRed, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun RecordingsTabContent(viewModel: MainViewModel) {
    val recordings by viewModel.allRecordings.collectAsStateWithLifecycle()
    val playingPath by viewModel.activeAudioPlaybackPath.collectAsStateWithLifecycle()

    if (recordings.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = BorderSlate, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Không có bản ghi âm", fontSize = 12.sp, color = SlateTextSecondary)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(recordings, key = { it.id }) { rec ->
                val isPlaying = playingPath == rec.audioPath
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceSlate)
                        .border(
                            1.dp,
                            if (isPlaying) GlowCyan else BorderSlate,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.toggleAudioPlayback(rec) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) GlowCyan else BorderSlate)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "PlayRecording",
                                    tint = if (isPlaying) Color.White else Color(0xFF44474E),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                val date = Date(rec.createdAt)
                                Text(
                                    "Audio Record (%.1fs)".format(Locale.US, rec.durationSeconds),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B1B1F)
                                )
                                Text(
                                    date.toString().substring(4, 16),
                                    fontSize = 9.sp,
                                    color = SlateTextSecondary
                                )
                            }
                        }

                        Row {
                            IconButton(
                                onClick = { viewModel.insertTranscriptIntoComposer(rec.transcript) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Input, contentDescription = "InsertToChat", tint = GlowCyan, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.deleteRecording(rec.id, rec.audioPath) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "DeleteAudio", tint = CoralRed, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    val parsedData = remember(rec.transcript) { TranscriptData.fromJson(rec.transcript) }
                    val displaySTT = remember(parsedData) { parsedData.segments.joinToString("\n") { it.text } }
                    val sourceStr = remember(parsedData) { parsedData.source }
                    val isPolishing by viewModel.isPolishingTranscript.collectAsStateWithLifecycle()

                    if (displaySTT.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(BorderSlate.copy(alpha = 0.3f))
                                .padding(8.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val badgeBg = if (sourceStr == "Gemini-Polished") ActiveGreen.copy(alpha = 0.2f) else GlowCyan.copy(alpha = 0.2f)
                                    val badgeText = if (sourceStr == "Gemini-Polished") ActiveGreen else GlowCyan
                                    val sourceLabel = if (sourceStr == "Gemini-Polished") "AI Polished ✨" else "STT Thô 🎙️"
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(badgeBg)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(sourceLabel, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = badgeText)
                                    }

                                    Button(
                                        onClick = { viewModel.polishTranscript(rec) },
                                        enabled = !isPolishing,
                                        colors = ButtonDefaults.buttonColors(containerColor = GlowCyan),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.height(18.dp)
                                    ) {
                                        if (isPolishing) {
                                            CircularProgressIndicator(modifier = Modifier.size(8.dp), color = Color.White, strokeWidth = 1.dp)
                                        } else {
                                            Text("Hiệu Đính AI 🌌", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    displaySTT,
                                    fontSize = 11.sp,
                                    color = SlateTextSecondary,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. FLOATING HUD OVERLAY MODE SIMULATOR (Float UI)
// ==========================================
@Composable
fun FloatSimulationView(viewModel: MainViewModel) {
    val durationMs by viewModel.recordingDurationMs.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecordingAudio.collectAsStateWithLifecycle()
    val rms by viewModel.recordingRmsAmplitude.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.isTranscribingAudio.collectAsStateWithLifecycle()
    val ocrText by viewModel.floatOcrResult.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordAudioGranted) {
            android.widget.Toast.makeText(context, "Quyền ghi âm (Microphone) bị từ chối. Vui lòng cấp quyền trong Cài đặt hệ thống để tiếp tục.", android.widget.Toast.LENGTH_LONG).show()
        } else {
            viewModel.toggleAudioRecording()
        }
    }

    val triggerFloatRecordingSafely = {
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.toggleAudioRecording()
        } else {
            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(android.Manifest.permission.RECORD_AUDIO)
            }
            permissionLauncher.launch(perms)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(2.dp, GlowCyan),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) CoralRed else ActiveGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "FLOAT HUD TRACE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlowCyan,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(
                        onClick = { viewModel.isFloatViewMode.value = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "DismissFloat", tint = SlateTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val micScaleLimit = if (isRecording) {
                    val scaleOffsetByRms = 1f + (rms * 4f).coerceAtMost(1.5f)
                    scaleOffsetByRms
                } else {
                    1f
                }
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isRecording) CoralRed.copy(alpha = 0.4f) else ElectricBlue.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                        .drawBehind {
                            drawCircle(
                                color = if (isRecording) CoralRed.copy(alpha = 0.2f) else ElectricBlue.copy(alpha = 0.2f),
                                radius = size.minDimension / 2 * micScaleLimit
                            )
                        }
                        .clickable { triggerFloatRecordingSafely() },
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { triggerFloatRecordingSafely() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) CoralRed else BorderSlate)
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "FloatMicTrigger",
                            tint = if (isRecording) Color.White else Color(0xFF44474E),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isRecording) {
                    val secs = durationMs / 1000
                    val dec = (durationMs % 1000) / 100
                    Text(
                        "Ghi âm Voice Active... ${secs}.${dec}s",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CoralRed
                    )
                    Text(
                        "Sóng màng Mic (RMS): %.5f".format(Locale.US, rms),
                        fontSize = 9.sp,
                        color = SlateTextSecondary
                    )
                } else if (isTranscribing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SoftOrange, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Model STT Transcribing...", fontSize = 11.sp, color = SoftOrange, fontWeight = FontWeight.Bold)
                } else {
                    Text("Bấm Mic để Thao Tác Tức Thời", fontSize = 13.sp, color = SlateTextSecondary)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = BorderSlate, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            android.widget.Toast.makeText(context, "Chức năng Chụp màn hình (MediaProjection OCR) đang được bảo trì. Vui lòng hỏi bằng giọng nói hoặc nhập văn bản trực tiếp.", android.widget.Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BorderSlate),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp), tint = SlateTextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chụp màn hình (MediaProjection OCR)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                    }
                }
                }

                if (ocrText != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BorderSlate)
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("COMPACT OCR DATA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GlowCyan)
                                IconButton(onClick = { viewModel.clearOcrResults() }, modifier = Modifier.size(18.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SlateTextSecondary, modifier = Modifier.size(10.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(ocrText ?: "", fontSize = 11.sp, color = Color(0xFF1B1B1F))

                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = {
                                    viewModel.isFloatViewMode.value = false
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("View details inside workspace →", fontSize = 10.sp, color = GlowCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. SETTINGS PANEL CONSOLE
// ==========================================
@Composable
fun SettingsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val modelChoices = listOf("gemini-1.5-flash", "gemini-1.5-pro")
    val sttChoices = listOf(
        "gemini-2.5-flash-native-audio-preview-12-2025",
        "gemini-1.5-flash"
    )

    var cModel by remember { mutableStateOf(viewModel.settingsManager.chatModel) }
    var sModel by remember { mutableStateOf(viewModel.settingsManager.sttModel) }
    var fsModel by remember { mutableStateOf(viewModel.settingsManager.fallbackSttModel) }
    var prompt by remember { mutableStateOf(viewModel.settingsManager.sttPrompt) }
    var vadStatus by remember { mutableStateOf(viewModel.settingsManager.autoStopSilenceEnabled) }
    var rmsValue by remember { mutableFloatStateOf(viewModel.settingsManager.silenceThreshold) }
    var silenceSeconds by remember { mutableIntStateOf(viewModel.settingsManager.maxSilenceSeconds) }

    var preferredProvider by remember { mutableStateOf(viewModel.settingsManager.preferredProvider) }
    var cEndpoint by remember { mutableStateOf(viewModel.settingsManager.customEndpointUrl) }
    var cModelName by remember { mutableStateOf(viewModel.settingsManager.customModelName) }
    var cApiKey by remember { mutableStateOf(viewModel.settingsManager.customApiKey) }
    
    var useOnDeviceStt by remember { mutableStateOf(viewModel.settingsManager.useOnDeviceRecognizer) }
    var speechLang by remember { mutableStateOf(viewModel.settingsManager.speechLanguage) }
    var ttsLang by remember { mutableStateOf(viewModel.settingsManager.ttsLanguage) }
    var ttsRateVal by remember { mutableStateOf(viewModel.settingsManager.ttsSpeechRate) }
    var ttsPitchVal by remember { mutableStateOf(viewModel.settingsManager.ttsPitch) }
    var voiceQAutoSend by remember { mutableStateOf(viewModel.settingsManager.voiceQuestionAutoSend) }
    var voiceQAutoPlayTts by remember { mutableStateOf(viewModel.settingsManager.voiceQuestionAutoPlayTts) }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var providersList by remember {
        mutableStateOf(LlmProvider.fromJsonArrayString(viewModel.settingsManager.providersJson))
    }
    var activeProviderId by remember {
        mutableStateOf(viewModel.settingsManager.activeProviderId)
    }

    var routeRoundRobin by remember { mutableStateOf(viewModel.settingsManager.routingStrategyRoundRobin) }
    var routeStickyLimit by remember { mutableStateOf(viewModel.settingsManager.routingStrategyStickyLimit) }
    var routeComboRoundRobin by remember { mutableStateOf(viewModel.settingsManager.routingStrategyComboRoundRobin) }
    var routeComboStickyLimit by remember { mutableStateOf(viewModel.settingsManager.routingStrategyComboStickyLimit) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderSlate)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "Settings Board",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlowCyan,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Divider(color = BorderSlate)

                Spacer(modifier = Modifier.height(10.dp))
                Text("Chat Core Model", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modelChoices.forEach { m ->
                        val isSelected = cModel == m
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ElectricBlue else BorderSlate)
                                .clickable { cModel = m }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(m, fontSize = 11.sp, color = if (isSelected) Color.White else Color(0xFF1B1B1F), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Primary STT Transcription Model", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                sttChoices.forEach { s ->
                    val isSelected = sModel == s
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) ElectricBlue.copy(alpha = 0.15f) else Color.Transparent)
                            .border(1.dp, if (isSelected) ElectricBlue else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { sModel = s }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { sModel = s }, colors = RadioButtonDefaults.colors(selectedColor = GlowCyan))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s, fontSize = 11.sp, color = Color(0xFF1B1B1F), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("STT Instructions / Guidance Prompt", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                TextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF1B1B1F)),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF3F3FA), unfocusedContainerColor = Color(0xFFF3F3FA))
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderSlate)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Auto-Stop on Silence", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                        Text("Stops mic on voice pauses", fontSize = 10.sp, color = SlateTextSecondary)
                    }
                    Switch(checked = vadStatus, onCheckedChange = { vadStatus = it }, colors = SwitchDefaults.colors(checkedThumbColor = GlowCyan))
                }

                if (vadStatus) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("RMS Silence Threshold (%.4f)".format(Locale.US, rmsValue), fontSize = 11.sp, color = SlateTextSecondary)
                    Slider(
                        value = rmsValue,
                        onValueChange = { rmsValue = it },
                        valueRange = 0.002f..0.05f,
                        colors = SliderDefaults.colors(thumbColor = GlowCyan, activeTrackColor = GlowCyan)
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Required Silence Delay: $silenceSeconds seconds", fontSize = 11.sp, color = SlateTextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 3, 5).forEach { time ->
                            val isSel = silenceSeconds == time
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSel) ElectricBlue else BorderSlate)
                                    .clickable { silenceSeconds = time }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${time}s", fontSize = 11.sp, color = if (isSel) Color.White else Color(0xFF1B1B1F))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderSlate)
                Spacer(modifier = Modifier.height(10.dp))

                Text("🤖 Manage LLM Providers", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GlowCyan)
                Spacer(modifier = Modifier.height(8.dp))

                var editingProviderId by remember { mutableStateOf<String?>(null) }
                var editName by remember { mutableStateOf("") }
                var editEndpoint by remember { mutableStateOf("") }
                var editApiKey by remember { mutableStateOf("") }
                var editModels by remember { mutableStateOf("") }
                var editMaxTokens by remember { mutableStateOf("4096") }

                providersList.forEach { provider ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                        border = BorderStroke(1.dp, BorderSlate)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = activeProviderId == provider.id,
                                        onClick = { activeProviderId = provider.id },
                                        colors = RadioButtonDefaults.colors(selectedColor = GlowCyan)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text(provider.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Models Count: ${provider.models.size}", fontSize = 10.sp, color = SlateTextSecondary)
                                    }
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            editingProviderId = provider.id
                                            editName = provider.name
                                            editEndpoint = provider.endpointUrl
                                            editApiKey = provider.apiKey
                                            editModels = provider.models.joinToString(", ")
                                            editMaxTokens = provider.maxTokens.toString()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = ElectricBlue, modifier = Modifier.size(16.dp))
                                    }
                                    if (provider.id != "gemini") {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                providersList = providersList.filter { it.id != provider.id }
                                                if (activeProviderId == provider.id) {
                                                    activeProviderId = "gemini"
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            if (editingProviderId == provider.id) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Edit Provider Info", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SoftOrange)
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Display Name", fontSize = 10.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(0xFF1B1B1F), unfocusedTextColor = Color(0xFF1B1B1F))
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = editEndpoint,
                                    onValueChange = { editEndpoint = it },
                                    label = { Text("Endpoint URL", fontSize = 10.sp) },
                                    placeholder = { Text("https://api.openai.com/v1", fontSize = 10.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(0xFF1B1B1F), unfocusedTextColor = Color(0xFF1B1B1F))
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = editApiKey,
                                    onValueChange = { editApiKey = it },
                                    label = { Text("API Key / Token", fontSize = 10.sp) },
                                    placeholder = { Text("sk-...", fontSize = 10.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(0xFF1B1B1F), unfocusedTextColor = Color(0xFF1B1B1F))
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = editModels,
                                    onValueChange = { editModels = it },
                                    label = { Text("Supported Models (comma-separated)", fontSize = 10.sp) },
                                    placeholder = { Text("gpt-4o, gpt-4o-mini", fontSize = 10.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(0xFF1B1B1F), unfocusedTextColor = Color(0xFF1B1B1F))
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                val maxTokensFloat = editMaxTokens.toFloatOrNull() ?: 4096f
                                val maxTokensCoerced = maxTokensFloat.coerceIn(1000f, 1000000f)
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                    Text(
                                        text = "Max Token Limits: ${String.format(Locale.US, "%,d", maxTokensCoerced.toInt())}",
                                        fontSize = 11.sp, 
                                        fontWeight = FontWeight.Bold, 
                                        color = SlateTextSecondary
                                    )
                                    Slider(
                                        value = maxTokensCoerced,
                                        onValueChange = { editMaxTokens = it.toInt().toString() },
                                        valueRange = 1000f..1000000f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = GlowCyan,
                                            activeTrackColor = GlowCyan,
                                            inactiveTrackColor = BorderSlate
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { editingProviderId = null }) {
                                        Text("Cancel", fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            val parsedModels = editModels.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                            val tokens = editMaxTokens.toIntOrNull() ?: 4096
                                            providersList = providersList.map {
                                                if (it.id == provider.id) {
                                                    it.copy(
                                                        name = editName.ifBlank { it.name },
                                                        endpointUrl = editEndpoint,
                                                        apiKey = editApiKey,
                                                        models = parsedModels,
                                                        maxTokens = tokens
                                                    )
                                                } else {
                                                    it
                                                }
                                            }
                                            editingProviderId = null
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GlowCyan)
                                    ) {
                                        Text("Apply Change", fontSize = 11.sp, color = CosmicDark)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        val newId = "provider_" + System.currentTimeMillis()
                        val newProvider = LlmProvider(
                            id = newId,
                            name = "OpenRouter Custom",
                            endpointUrl = "https://openrouter.ai/api/v1",
                            apiKey = "",
                            models = listOf("deepseek/deepseek-chat", "meta-llama/llama-3-70b-instruct"),
                            maxTokens = 4096
                        )
                        providersList = providersList + newProvider
                        editingProviderId = newId
                        editName = newProvider.name
                        editEndpoint = newProvider.endpointUrl
                        editApiKey = newProvider.apiKey
                        editModels = newProvider.models.joinToString(", ")
                        editMaxTokens = newProvider.maxTokens.toString()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add New Provider Setup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Backup, Share & Restore Config", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))

                var jsonBackupText by remember { mutableStateOf("") }
                var showBackupArea by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val json = LlmProvider.toJsonArrayString(providersList)
                            jsonBackupText = json
                            showBackupArea = true
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(json))
                            android.widget.Toast.makeText(context, "Copied LLM Config JSON to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Copy Backup text", fontSize = 10.sp)
                    }

                    Button(
                        onClick = {
                            showBackupArea = true
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Paste / Edit JSON", fontSize = 10.sp)
                    }
                }

                if (showBackupArea) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jsonBackupText,
                        onValueChange = { jsonBackupText = it },
                        label = { Text("Backup Configuration JSON String", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        textStyle = TextStyle(fontSize = 11.sp, color = Color(0xFF1B1B1F)),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(0xFF1B1B1F), unfocusedTextColor = Color(0xFF1B1B1F))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val imported = LlmProvider.fromJsonArrayString(jsonBackupText)
                                    if (imported.isNotEmpty()) {
                                        providersList = imported
                                        android.widget.Toast.makeText(context, "Successfully Imported ${imported.size} LLM settings!", android.widget.Toast.LENGTH_SHORT).show()
                                        showBackupArea = false
                                    } else {
                                        android.widget.Toast.makeText(context, "Error: Empty or Invalid JSON array", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SoftOrange)
                        ) {
                            Text("Apply JSON String", fontSize = 10.sp, color = CosmicDark, fontWeight = FontWeight.Bold)
                        }
                        
                        OutlinedButton(
                            onClick = { showBackupArea = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hide text", fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderSlate)
                Spacer(modifier = Modifier.height(10.dp))

                // Routing Strategy Section
                Text("🌐 LLM Routing Strategy", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GlowCyan)
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 1. Round Robin
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Round Robin Strategy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Cycle through accounts systematically for queries", fontSize = 9.sp, color = SlateTextSecondary)
                            }
                            Switch(
                                checked = routeRoundRobin,
                                onCheckedChange = { 
                                    routeRoundRobin = it
                                    if (it) {
                                        // Disable combo round robin if standard round robin enables (mutually exclusive)
                                        routeComboRoundRobin = false
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = GlowCyan)
                            )
                        }

                        if (routeRoundRobin) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Sticky Query Limit", fontSize = 11.sp, color = SlateTextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { routeStickyLimit = (routeStickyLimit - 1).coerceAtLeast(1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("-", color = GlowCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Text(
                                        text = "$routeStickyLimit",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    IconButton(
                                        onClick = { routeStickyLimit = (routeStickyLimit + 1).coerceAtMost(50) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("+", color = GlowCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = BorderSlate.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. Combo Round Robin
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Combo Round Robin", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Cycle over Account-Model pairs instead of provider-only selection", fontSize = 9.sp, color = SlateTextSecondary)
                            }
                            Switch(
                                checked = routeComboRoundRobin,
                                onCheckedChange = { 
                                    routeComboRoundRobin = it
                                    if (it) {
                                        // Disable standard round robin if combo enables
                                        routeRoundRobin = false
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = GlowCyan)
                            )
                        }

                        if (routeComboRoundRobin) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Combo Sticky Limit", fontSize = 11.sp, color = SlateTextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { routeComboStickyLimit = (routeComboStickyLimit - 1).coerceAtLeast(1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("-", color = GlowCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Text(
                                        text = "$routeComboStickyLimit",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    IconButton(
                                        onClick = { routeComboStickyLimit = (routeComboStickyLimit + 1).coerceAtMost(50) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("+", color = GlowCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val routingDescription = when {
                    routeComboRoundRobin -> {
                        "Active Route: Combo Round-Robin (sticky limit $routeComboStickyLimit queries before rotation)."
                    }
                    routeRoundRobin -> {
                        "Active Route: Multi-Account Round-Robin (sticky limit $routeStickyLimit queries before rotation)."
                    }
                    else -> {
                        "Active Route: Single Client Sticky. Using selected provider '$activeProviderId' exclusively."
                    }
                }
                Text(
                    text = routingDescription,
                    fontSize = 10.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = SlateTextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderSlate)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("On-device Voice Recognition", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                        Text("Transcription via Android engine", fontSize = 10.sp, color = SlateTextSecondary)
                    }
                    Switch(checked = useOnDeviceStt, onCheckedChange = { useOnDeviceStt = it }, colors = SwitchDefaults.colors(checkedThumbColor = GlowCyan))
                }

                if (useOnDeviceStt) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("On-device Speech Language", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("vi-VN" to "Việt Nam", "en-US" to "English", "ja-JP" to "日本語").forEach { (code, name) ->
                            val isSelected = speechLang == code
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) ElectricBlue else BorderSlate)
                                    .clickable { speechLang = code }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(name, fontSize = 11.sp, color = if (isSelected) Color.White else Color(0xFF1B1B1F))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Local TTS Language Selection", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("vi" to "Việt Nam", "en" to "English", "ja" to "日本語").forEach { (code, name) ->
                        val isSelected = ttsLang == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ElectricBlue else BorderSlate)
                                .clickable { ttsLang = code }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, fontSize = 11.sp, color = if (isSelected) Color.White else Color(0xFF1B1B1F))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TTS Speech Speed: ${"%.2f".format(ttsRateVal)}x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                    TextButton(onClick = { ttsRateVal = 1.0f }) {
                        Text("Reset", fontSize = 10.sp, color = ElectricBlue)
                    }
                }
                Slider(
                    value = ttsRateVal,
                    onValueChange = { ttsRateVal = it },
                    valueRange = 0.5f..2.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlue,
                        activeTrackColor = ElectricBlue,
                        inactiveTrackColor = BorderSlate
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TTS Voice Pitch: ${"%.2f".format(ttsPitchVal)}x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                    TextButton(onClick = { ttsPitchVal = 1.0f }) {
                        Text("Reset", fontSize = 10.sp, color = ElectricBlue)
                    }
                }
                Slider(
                    value = ttsPitchVal,
                    onValueChange = { ttsPitchVal = it },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlue,
                        activeTrackColor = ElectricBlue,
                        inactiveTrackColor = BorderSlate
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderSlate)
                Spacer(modifier = Modifier.height(10.dp))

                Text("Hỏi Bằng Giọng Nói (Voice Question)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlowCyan)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tự Động Gửi Tin Nhắn", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                        Text("Gửi ngay văn bản STT câu hỏi thô cho LLM", fontSize = 10.sp, color = SlateTextSecondary)
                    }
                    Switch(checked = voiceQAutoSend, onCheckedChange = { voiceQAutoSend = it }, colors = SwitchDefaults.colors(checkedThumbColor = GlowCyan))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tự Động Phát Âm Binh (TTS)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                        Text("Nói to câu trả lời của LLM ngay khi có phản hồi", fontSize = 10.sp, color = SlateTextSecondary)
                    }
                    Switch(checked = voiceQAutoPlayTts, onCheckedChange = { voiceQAutoPlayTts = it }, colors = SwitchDefaults.colors(checkedThumbColor = GlowCyan))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss", color = SlateTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.settingsManager.chatModel = cModel
                            viewModel.settingsManager.sttModel = sModel
                            viewModel.settingsManager.sttPrompt = prompt
                            viewModel.settingsManager.autoStopSilenceEnabled = vadStatus
                            viewModel.settingsManager.silenceThreshold = rmsValue
                            viewModel.settingsManager.maxSilenceSeconds = silenceSeconds

                            viewModel.settingsManager.providersJson = LlmProvider.toJsonArrayString(providersList)
                            viewModel.settingsManager.activeProviderId = activeProviderId

                            val activeObj = providersList.find { it.id == activeProviderId }
                            if (activeObj != null) {
                                viewModel.settingsManager.preferredProvider = if (activeProviderId == "gemini") "gemini" else "openai_compatible"
                                viewModel.settingsManager.customEndpointUrl = activeObj.endpointUrl
                                viewModel.settingsManager.customModelName = activeObj.models.firstOrNull() ?: ""
                                viewModel.settingsManager.customApiKey = activeObj.apiKey
                            }

                            viewModel.settingsManager.useOnDeviceRecognizer = useOnDeviceStt
                            viewModel.settingsManager.speechLanguage = speechLang
                            viewModel.settingsManager.ttsLanguage = ttsLang
                            viewModel.settingsManager.ttsSpeechRate = ttsRateVal
                            viewModel.settingsManager.ttsPitch = ttsPitchVal
                            viewModel.settingsManager.voiceQuestionAutoSend = voiceQAutoSend
                            viewModel.settingsManager.voiceQuestionAutoPlayTts = voiceQAutoPlayTts

                            // Save routing configuration
                            viewModel.settingsManager.routingStrategyRoundRobin = routeRoundRobin
                            viewModel.settingsManager.routingStrategyStickyLimit = routeStickyLimit
                            viewModel.settingsManager.routingStrategyComboRoundRobin = routeComboRoundRobin
                            viewModel.settingsManager.routingStrategyComboStickyLimit = routeComboStickyLimit

                            // Clear existing stale cooldowns since configuration just updated
                            com.example.data.LlmRouteSelector.clearAllCooldowns()

                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GlowCyan)
                    ) {
                        Text("Save Configurations", color = CosmicDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomSkillDialog(onDismiss: () -> Unit, onConfirmAdd: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Add Custom Prompt Skill", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GlowCyan)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Skill Name (e.g. Kotlin Assistant)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1B1B1F),
                        unfocusedTextColor = Color(0xFF1B1B1F),
                        focusedBorderColor = GlowCyan,
                        unfocusedBorderColor = BorderSlate
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("System Prompt Guidance Content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1B1B1F),
                        unfocusedTextColor = Color(0xFF1B1B1F),
                        focusedBorderColor = GlowCyan,
                        unfocusedBorderColor = BorderSlate
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SlateTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirmAdd(title, content) },
                        enabled = title.isNotBlank() && content.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = GlowCyan)
                    ) {
                        Text("Add", color = CosmicDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
