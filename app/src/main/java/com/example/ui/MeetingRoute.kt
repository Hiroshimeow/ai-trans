package com.example.ui

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainViewModel

@Composable
fun MeetingRoute(
    viewModel: MainViewModel,
    onStartMeetingClick: () -> Unit
) {
    DetailsPanel(viewModel = viewModel, onStartMeetingClick = onStartMeetingClick)
}
