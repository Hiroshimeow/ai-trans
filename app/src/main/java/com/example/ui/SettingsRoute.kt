package com.example.ui

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainViewModel

@Composable
fun SettingsRoute(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    SettingsDialog(viewModel = viewModel, onDismiss = onDismiss)
}
