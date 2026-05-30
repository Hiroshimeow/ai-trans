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


