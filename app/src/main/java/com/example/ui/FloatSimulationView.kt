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
fun FloatSimulationView(viewModel: MainViewModel) {
    val durationMs by viewModel.recordingDurationMs.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecordingAudio.collectAsStateWithLifecycle()
    val rms by viewModel.recordingRmsAmplitude.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.isTranscribingAudio.collectAsStateWithLifecycle()

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
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    "Giao diện Quick Overlay đã sẵn sàng phục vụ.",
                    fontSize = 11.sp,
                    color = SlateTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
            }
        }
    }
}



