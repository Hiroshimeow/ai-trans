package com.example.ui

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainViewModel

@Composable
fun ChatRoute(
    viewModel: MainViewModel,
    triggerRecordingSafely: () -> Unit,
    triggerVoiceQuestionSafely: () -> Unit
) {
    ChatPanel(
        viewModel = viewModel,
        triggerRecordingSafely = triggerRecordingSafely,
        triggerVoiceQuestionSafely = triggerVoiceQuestionSafely
    )
}
