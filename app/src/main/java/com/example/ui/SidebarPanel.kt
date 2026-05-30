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
