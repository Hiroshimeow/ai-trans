package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainViewModel
import com.example.ui.theme.GlowCyan

@Composable
fun DetailsPanel(viewModel: MainViewModel, onStartMeetingClick: () -> Unit) {
    var activeTabIdx by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activeTabIdx,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = GlowCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTabIdx]),
                    color = GlowCyan
                )
            }
        ) {
            Tab(
                selected = activeTabIdx == 0,
                onClick = { activeTabIdx = 0 },
                text = { Text("Attachments", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTabIdx == 1,
                onClick = { activeTabIdx = 1 },
                text = { Text("Record Library", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTabIdx == 2,
                onClick = { activeTabIdx = 2 },
                text = { Text("Meeting 🎙️", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(10.dp)
        ) {
            when (activeTabIdx) {
                0 -> AttachmentsTabContent(viewModel = viewModel)
                1 -> RecordingsTabContent(viewModel = viewModel)
                2 -> MeetingTabContent(viewModel = viewModel, onStartMeetingClick = onStartMeetingClick)
            }
        }
    }
}
