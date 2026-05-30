package com.example.mcp

import com.example.data.CredentialStore
import com.example.data.McpDao
import com.example.data.McpServerEntity
import com.example.data.McpToolEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class McpRepository(
    private val mcpDao: McpDao,
    private val mcpClient: McpClient,
    private val credentialStore: CredentialStore
) {
    fun getAllServersFlow(): Flow<List<McpServerEntity>> = mcpDao.getAllServersFlow()

    suspend fun addServer(name: String, baseUrl: String, token: String) {
        val serverId = UUID.randomUUID().toString()
        val tokenAlias = "mcp_token_$serverId"
        
        credentialStore.saveSecret(tokenAlias, token)
        
        val server = McpServerEntity(
            id = serverId,
            name = name,
            baseUrl = baseUrl,
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

    suspend fun getEnabledServersSync(): List<McpServerEntity> {
        return mcpDao.getEnabledServersSync()
    }

    suspend fun getEnabledToolsForServer(serverId: String): List<McpToolEntity> {
        return mcpDao.getToolsForServerSync(serverId).filter { it.enabled }
    }

    suspend fun executeTool(serverId: String, toolName: String, argsJson: String): com.example.mcp.McpToolResult {
        val server = mcpDao.getEnabledServersSync().find { it.id == serverId }
            ?: throw IllegalStateException("Server not found or disabled: $serverId")
        
        val config = McpServerConfig(
            id = server.id,
            name = server.name,
            baseUrl = server.baseUrl,
            tokenAlias = server.tokenAlias,
            enabled = server.enabled
        )
        return mcpClient.callTool(config, toolName, argsJson)
    }

    suspend fun testAndRefreshTools(server: McpServerEntity): Result<Unit> {
        return try {
            val config = McpServerConfig(
                id = server.id,
                name = server.name,
                baseUrl = server.baseUrl,
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

            // Save tools
            val toolEntities = tools.map { t ->
                McpToolEntity(
                    id = "${server.id}_${t.name}",
                    serverId = server.id,
                    name = t.name,
                    description = t.description,
                    inputSchemaJson = t.inputSchema,
                    enabled = true,
                    updatedAt = System.currentTimeMillis()
                )
            }
            mcpDao.insertTools(toolEntities)
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
