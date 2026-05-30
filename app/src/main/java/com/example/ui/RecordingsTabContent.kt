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

