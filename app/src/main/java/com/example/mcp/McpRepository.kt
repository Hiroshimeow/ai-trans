package com.example.mcp

import com.example.data.CredentialStore
import com.example.data.McpDao
import com.example.data.McpServerEntity
import com.example.data.McpToolEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class McpRepository(
    private val mcpDao: McpDao,
    private val toolCallDao: com.example.data.ToolCallDao,
    private val mcpClient: McpClient,
    private val credentialStore: CredentialStore,
    private val runtimeConfigRepo: com.example.data.RuntimeConfigRepository
) {
    fun getAllServersFlow(): Flow<List<McpServerEntity>> = mcpDao.getAllServersFlow()
    fun getToolsForServerFlow(serverId: String): Flow<List<McpToolEntity>> = mcpDao.getToolsForServerFlow(serverId)

    private fun getConfigServers(): List<McpServerEntity> {
        return try {
            val config = runtimeConfigRepo.loadConfig()
            config.mcp.servers.filter { it.enabled }.map {
                McpServerEntity(
                    id = it.id.ifEmpty { java.util.UUID.nameUUIDFromBytes(it.endpoint.toByteArray()).toString() },
                    name = it.name.ifEmpty { "Configured Server" },
                    endpointUrl = it.endpoint,
                    tokenAlias = it.tokenAlias,
                    enabled = it.enabled,
                    createdAt = 0L,
                    updatedAt = 0L,
                    lastConnectedAt = null,
                    lastError = null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEnabledServersSync(): List<McpServerEntity> {
        val dbServers = mcpDao.getEnabledServersSync()
        val configServers = getConfigServers()
        
        // Merge so that Config Servers take priority, avoiding ID duplication
        val merged = mutableListOf<McpServerEntity>()
        merged.addAll(configServers)
        dbServers.forEach { db ->
            if (merged.none { it.id == db.id } && merged.none { it.endpointUrl == db.endpointUrl }) {
                merged.add(db)
            }
        }
        return merged
    }

    suspend fun addServer(name: String, endpointUrl: String, token: String) {
        val serverId = UUID.randomUUID().toString()
        val tokenAlias = "mcp_token_$serverId"
        
        credentialStore.saveSecret(tokenAlias, token)
        
        val server = McpServerEntity(
            id = serverId,
            name = name,
            endpointUrl = endpointUrl,
            tokenAlias = tokenAlias,
            enabled = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastConnectedAt = null,
            lastError = null
        )
        mcpDao.insertServer(server)
    }
    
    suspend fun removeServer(serverId: String) {
        // Technically should get the server first to delete its token, 
        // doing a quick cleanup for MVP
        credentialStore.deleteSecret("mcp_token_$serverId")
        mcpDao.deleteToolsByServerId(serverId)
        mcpDao.deleteServerById(serverId)
    }

    suspend fun getEnabledToolsForServer(serverId: String): List<McpToolEntity> {
        return mcpDao.getToolsForServerSync(serverId).filter { it.enabled }
    }

    suspend fun executeTool(sessionId: String, messageId: String, serverId: String, toolName: String, argsJson: String): com.example.mcp.McpToolResult {
        val server = getEnabledServersSync().find { it.id == serverId }
            ?: throw IllegalStateException("Server not found or disabled: $serverId")
        
        val config = McpServerConfig(
            id = server.id,
            name = server.name,
            endpointUrl = server.endpointUrl,
            tokenAlias = server.tokenAlias,
            enabled = server.enabled
        )

        // Policy Check
        val lowerName = toolName.lowercase()
        val isDestructive = lowerName.contains("shell") || lowerName.contains("delete") || 
                            lowerName.contains("move") || lowerName.contains("write") || 
                            lowerName.contains("push") || lowerName.contains("execute")

        val callId = UUID.randomUUID().toString()
        val toolCall = com.example.data.ToolCallEntity(
            id = callId,
            sessionId = sessionId,
            messageId = messageId,
            serverId = serverId,
            toolName = toolName,
            argumentsJson = argsJson,
            resultSummary = null,
            status = if (isDestructive) "pending_approval" else "executing",
            errorCode = null,
            isDestructive = isDestructive,
            createdAt = System.currentTimeMillis(),
            executedAt = null
        )
        toolCallDao.insertToolCall(toolCall)

        if (isDestructive) {
            toolCallDao.updateToolCall(toolCall.copy(
                status = "rejected",
                errorCode = "ToolApprovalRequired",
                resultSummary = "Destructive tool execution blocked. User approval UI flow is pending implementation."
            ))
            throw Exception("ToolApprovalRequired: Destructive tools require user approval which is not yet available.")
        }

        return try {
            val result = mcpClient.callTool(config, toolName, argsJson)
            toolCallDao.updateToolCall(toolCall.copy(
                status = "executed",
                resultSummary = "Success: ${result.content.take(100)}...",
                executedAt = System.currentTimeMillis()
            ))
            result
        } catch (e: Exception) {
            toolCallDao.updateToolCall(toolCall.copy(
                status = "failed",
                errorCode = e.javaClass.simpleName,
                resultSummary = e.message
            ))
            throw e
        }
    }

    suspend fun testAndRefreshTools(server: McpServerEntity): Result<Unit> {
        return try {
            val config = McpServerConfig(
                id = server.id,
                name = server.name,
                endpointUrl = server.endpointUrl,
                tokenAlias = server.tokenAlias,
                enabled = server.enabled
            )
            val tools = mcpClient.listTools(config)
            
            // Update lastConnectedAt
            mcpDao.updateServer(
                server.copy(
                    lastConnectedAt = System.currentTimeMillis(),
                    lastError = null
                )
            )

            // Save tools with validation
            val nameCounts = tools.groupingBy { it.name }.eachCount()
            val toolEntities = tools.map { t ->
                val isValidName = t.name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_-]*\$"))
                val isDuplicate = (nameCounts[t.name] ?: 0) > 1
                
                val statusMsg = when {
                    !isValidName -> "Invalid name. Use alias."
                    isDuplicate -> "Duplicate name."
                    else -> "Valid"
                }

                McpToolEntity(
                    id = "${server.id}_${t.name}",
                    serverId = server.id,
                    name = t.name, // Keep original name, UI will show it as disabled
                    description = t.description,
                    inputSchemaJson = t.inputSchema,
                    enabled = isValidName && !isDuplicate,
                    updatedAt = System.currentTimeMillis(),
                    status = if (isValidName && !isDuplicate) "active" else "disabled",
                    errorMessage = if (isValidName && !isDuplicate) null else statusMsg
                )
            }
            mcpDao.insertTools(toolEntities)
            
            // Run global validation for cross-server duplicates
            ToolCatalogValidator.validateAndDisableDuplicates(mcpDao)
            
            Result.success(Unit)
        } catch (e: Exception) {
            mcpDao.updateServer(
                server.copy(
                    lastError = e.message
                )
            )
            Result.failure(e)
        }
    }
}
