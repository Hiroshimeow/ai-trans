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

// ==========================================
// 5. SETTINGS PANEL CONSOLE
// ==========================================
@Composable
fun SettingsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
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

    val activeProvider = providersList.find { it.id == activeProviderId } ?: providersList.firstOrNull { it.id == "gemini" } ?: providersList.first()
    val modelChoices = activeProvider.models

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
                Text("Chat Core Model (của Provider đang chọn)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                modelChoices.chunked(2).forEach { rowModels ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowModels.forEach { m ->
                            val isSelected = cModel == m
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) ElectricBlue else BorderSlate.copy(alpha = 0.5f))
                                    .clickable { cModel = m }
                                    .padding(vertical = 8.dp, horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = m,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else Color(0xFF1B1B1F),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (rowModels.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Primary STT Transcription Model (${modelChoices.size} options)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                modelChoices.forEach { s ->
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
                        Text(s, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F7)),
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
                                        onClick = { 
                                            activeProviderId = provider.id 
                                            cModel = provider.models.firstOrNull() ?: ""
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = GlowCyan)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text(provider.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
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

                                if (provider.id != "gemini") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = editEndpoint,
                                        onValueChange = { editEndpoint = it },
                                        label = { Text("Endpoint URL", fontSize = 10.sp) },
                                        placeholder = { Text("https://api.your-provider.example/v1", fontSize = 10.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = TextStyle(fontSize = 11.sp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(0xFF1B1B1F), unfocusedTextColor = Color(0xFF1B1B1F))
                                    )
                                }

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
                                    placeholder = { Text("provider-model-a, provider-model-b", fontSize = 10.sp) },
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
                                        color = Color(0xFF1B1B1F)
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
                
                var showAddProviderMenu by remember { mutableStateOf(false) }
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showAddProviderMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add New Provider Setup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    DropdownMenu(
                        expanded = showAddProviderMenu,
                        onDismissRequest = { showAddProviderMenu = false }
                    ) {
                        val addTemplate = { name: String, endpointUrl: String, models: List<String>, protocol: com.example.data.ProviderProtocol ->
                            showAddProviderMenu = false
                            val newId = "provider_" + System.currentTimeMillis()
                            val newProvider = LlmProvider(
                                id = newId,
                                name = name,
                                endpointUrl = endpointUrl,
                                apiKey = "",
                                models = models,
                                maxTokens = 4096,
                                protocol = protocol
                            )
                            providersList = providersList + newProvider
                            editingProviderId = newId
                            editName = newProvider.name
                            editEndpoint = newProvider.endpointUrl
                            editApiKey = newProvider.apiKey
                            editModels = newProvider.models.joinToString(", ")
                            editMaxTokens = newProvider.maxTokens.toString()
                        }
                        
                        DropdownMenuItem(
                            text = { Text("OpenAI-compatible") },
                            onClick = { addTemplate("OpenAI-compatible custom", "", listOf("placeholder-model"), com.example.data.ProviderProtocol.OpenAiChatCompletions) }
                        )
                        DropdownMenuItem(
                            text = { Text("OpenRouter") },
                            onClick = { addTemplate("OpenRouter", "https://openrouter.ai/api/v1", listOf("deepseek/deepseek-chat", "meta-llama/llama-3-70b-instruct"), com.example.data.ProviderProtocol.CustomHttp) }
                        )
                        DropdownMenuItem(
                            text = { Text("Ollama (Local)") },
                            onClick = { addTemplate("Ollama Local", "http://10.0.2.2:11434/v1", listOf("llama3"), com.example.data.ProviderProtocol.OllamaChatCompletions) }
                        )
                        DropdownMenuItem(
                            text = { Text("Custom Gemini Endpoint") },
                            onClick = { addTemplate("Custom Gemini", "", listOf("gemini-2.5-flash", "gemini-1.5-pro"), com.example.data.ProviderProtocol.GeminiGenerateContent) }
                        )
                    }
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F7)),
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
                                Text("Round Robin Strategy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
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
                                        color = Color(0xFF1B1B1F),
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
                                Text("Combo Round Robin", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
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
                                Text("Combo Sticky Limit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
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
                                        color = Color(0xFF1B1B1F),
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
                
                McpSettingsSection(viewModel = viewModel)

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
                                viewModel.settingsManager.preferredProvider = if (activeProviderId == "gemini") "gemini" else "custom"
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
