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
    val pendingToolCalls by viewModel.pendingToolCalls.collectAsStateWithLifecycle()

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

        if (pendingToolCalls.isNotEmpty()) {
            val pendingForSession = pendingToolCalls.filter { it.sessionId == activeId }
            if (pendingForSession.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = CoralRed.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, CoralRed)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Pending Tool Approvals", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CoralRed)
                        Spacer(modifier = Modifier.height(4.dp))
                        pendingForSession.forEach { call ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Tool: ${call.toolName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Args: ${call.argumentsJson}", fontSize = 10.sp, color = SlateTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = { viewModel.approveToolCall(call.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Approve", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Button(
                                        onClick = { viewModel.rejectToolCall(call.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Reject", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
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



