package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

enum class RecordingAction {
    QuickVoice,
    VoiceQuestion,
    Meeting
}

class RecordingPermissionRequester(
    private val context: Context,
    private val onGranted: (RecordingAction) -> Unit
) {
    internal var pendingAction by mutableStateOf<RecordingAction?>(null)
    internal var launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null

    fun request(action: RecordingAction) {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            onGranted(action)
        } else {
            pendingAction = action
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            launcher?.launch(perms)
        }
    }
}

@Composable
fun rememberRecordingPermissionRequester(onGranted: (RecordingAction) -> Unit): RecordingPermissionRequester {
    val context = LocalContext.current
    val requester = remember(context, onGranted) { RecordingPermissionRequester(context, onGranted) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordAudioGranted) {
            Toast.makeText(context, "Quyền ghi âm (Microphone) bị từ chối. Vui lòng cấp quyền trong Cài đặt hệ thống để tiếp tục.", Toast.LENGTH_LONG).show()
        } else {
            requester.pendingAction?.let { onGranted(it) }
        }
        requester.pendingAction = null
    }
    
    requester.launcher = permissionLauncher
    return requester
}
