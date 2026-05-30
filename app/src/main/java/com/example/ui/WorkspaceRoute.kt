package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainViewModel

data class WorkspaceUiState(
    val isFloatViewMode: Boolean = false,
    val errorString: String? = null,
    val activeSessionId: String? = null,
    // Provide a partial view of state just for skeleton
)

sealed interface WorkspaceEvent {
    data object ClearError : WorkspaceEvent
    data class SwitchToFloatMode(val float: Boolean) : WorkspaceEvent
    data class RequestPermission(val action: com.example.ui.components.RecordingAction) : WorkspaceEvent
    // ...other events
}

@Composable
fun WorkspaceRoute(viewModel: MainViewModel) {
    // Collect state from ViewModel to construct a single UI State (Skeleton)
    val isFloatMode by viewModel.isFloatViewMode.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorString.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()

    val uiState = WorkspaceUiState(
        isFloatViewMode = isFloatMode,
        errorString = errorMsg,
        activeSessionId = activeSessionId
    )

    // For now, delegate entirely to existing UI, but wrap it in the route
    ScreenChatWorkspaceApp(viewModel = viewModel)
}
