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
    val permissionRequester = com.example.ui.components.rememberRecordingPermissionRequester { action ->
        when (action) {
            com.example.ui.components.RecordingAction.QuickVoice -> viewModel.toggleAudioRecording()
            com.example.ui.components.RecordingAction.VoiceQuestion -> viewModel.toggleVoiceQuestionRecording()
            com.example.ui.components.RecordingAction.Meeting -> viewModel.startMeetingRecording()
        }
    }

    val triggerRecordingSafely = { permissionRequester.request(com.example.ui.components.RecordingAction.QuickVoice) }
    val triggerVoiceQuestionSafely = { permissionRequester.request(com.example.ui.components.RecordingAction.VoiceQuestion) }
    val triggerMeetingSafely = { permissionRequester.request(com.example.ui.components.RecordingAction.Meeting) }


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

            if (showSettingsDialog) {
        SettingsRoute(
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
                MeetingRoute(viewModel = viewModel, onStartMeetingClick = triggerMeetingSafely)
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
                ChatRoute(
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
                    MeetingRoute(viewModel = viewModel, onStartMeetingClick = triggerMeetingSafely)
                }
            }
        }
    }
}

// ==========================================

