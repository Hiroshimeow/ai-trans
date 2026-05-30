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
fun MeetingTabContent(viewModel: MainViewModel, onStartMeetingClick: () -> Unit) {
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
                        color = Color(0xFF1B1B1F)
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
                val sentences = remember(liveTranscript) {
                    liveTranscript.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                }
                val lazyListState = rememberLazyListState()
                LaunchedEffect(sentences.size) {
                    if (sentences.isNotEmpty()) {
                        lazyListState.animateScrollToItem(sentences.size - 1)
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sentences.size) { index ->
                            val sentence = sentences[index]
                            val isPrimary = index % 2 == 0
                            val speaker = if (isPrimary) "Speaker 1 (User)" else "Speaker 2"
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(if (isPrimary) ElectricBlue else GlowCyan),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isPrimary) "1" else "2",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CosmicDark
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = speaker,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPrimary) ElectricBlue else GlowCyan
                                    )
                                }
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isPrimary) BorderSlate.copy(alpha = 0.3f) else SurfaceSlate
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.5.dp, BorderSlate.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = sentence,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1B1B1F),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chèn nội dung vào ô soạn thảo:", fontSize = 10.sp, color = SlateTextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
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
                    "Workspace cố gắng lưu transcript nháp định kỳ xuống cache khi có cập nhật live.",
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
                    onClick = { onStartMeetingClick() },
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


