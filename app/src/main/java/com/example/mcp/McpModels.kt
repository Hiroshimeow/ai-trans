package com.example.mcp

data class McpServerConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val tokenAlias: String,
    val enabled: Boolean,
    val timeoutMs: Long = 30_000L
)

data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: String
)

data class McpToolResult(
    val isError: Boolean,
    val content: String // JSON array of content items or single string
)

interface McpClient {
    suspend fun listTools(server: McpServerConfig): List<McpToolDefinition>
    suspend fun callTool(server: McpServerConfig, name: String, argumentsJson: String): McpToolResult
}
