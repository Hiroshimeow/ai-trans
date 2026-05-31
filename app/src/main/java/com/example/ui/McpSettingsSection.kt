package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainViewModel
import com.example.data.McpServerEntity
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun McpSettingsSection(viewModel: MainViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val servers by viewModel.mcpRepository.getAllServersFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    var newServerUrl by remember { mutableStateOf("") }
    var newServerToken by remember { mutableStateOf("") }
    
    var showMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFF1B1B1F), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text("MCP Tool Gateway", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlowCyan)
        Text("Integrate external tools using the Model Context Protocol", fontSize = 11.sp, color = SlateTextSecondary)
        
        Spacer(modifier = Modifier.height(12.dp))

        servers.forEach { server ->
            McpServerRow(
                viewModel = viewModel,
                server = server,
                onRemove = {
                    coroutineScope.launch {
                        viewModel.mcpRepository.removeServer(server.id)
                    }
                },
                onRefresh = {
                    coroutineScope.launch {
                        showMessage = "Testing connection..."
                        val result = viewModel.mcpRepository.testAndRefreshTools(server)
                        showMessage = if (result.isSuccess) {
                            "Successfully connected & refreshed tools"
                        } else {
                            "Connection failed: ${result.exceptionOrNull()?.message}"
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = newServerUrl,
            onValueChange = { newServerUrl = it },
            label = { Text("MCP Endpoint URL", fontSize = 11.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = newServerToken,
            onValueChange = { newServerToken = it },
            label = { Text("API Token", fontSize = 11.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (newServerUrl.isNotBlank() && newServerToken.isNotBlank()) {
                    coroutineScope.launch {
                        viewModel.mcpRepository.addServer("MCP Server", newServerUrl, newServerToken)
                        newServerUrl = ""
                        newServerToken = ""
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GlowCyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add MCP Server", color = CosmicDark, fontWeight = FontWeight.Bold)
        }
        
        if (showMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(showMessage!!, color = ElectricBlue, fontSize = 11.sp)
        }
    }
}

@Composable
fun McpServerRow(
    viewModel: MainViewModel,
    server: McpServerEntity,
    onRemove: () -> Unit,
    onRefresh: () -> Unit
) {
    val tools by viewModel.mcpRepository.getToolsForServerFlow(server.id).collectAsStateWithLifecycle(initialValue = emptyList())
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, BorderSlate, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Text(server.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
        Text(server.endpointUrl, color = SlateTextSecondary, fontSize = 11.sp)
        
        if (server.lastError != null) {
            Text("Error: \${server.lastError}", color = Color.Red, fontSize = 10.sp)
        } else if (server.lastConnectedAt != null) {
            Text("Active & Connected: \${tools.size} tools", color = Color.Green, fontSize = 10.sp)
        }

        if (tools.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text("Available Tools:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                tools.forEach { tool ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(tool.name, color = if (tool.enabled) Color.White else SlateTextSecondary, fontSize = 11.sp, textDecoration = if (tool.enabled) null else androidx.compose.ui.text.style.TextDecoration.LineThrough)
                            if (tool.errorMessage != null) {
                                Text(tool.errorMessage, color = Color.Red, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Test & Refresh Tools", fontSize = 10.sp)
            }
            
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("Remove", fontSize = 10.sp)
            }
        }
    }
}
