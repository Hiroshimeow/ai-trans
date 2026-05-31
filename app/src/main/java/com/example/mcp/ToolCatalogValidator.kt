package com.example.mcp

import com.example.data.McpDao
import com.example.data.McpToolEntity

object ToolCatalogValidator {
    suspend fun validateAndDisableDuplicates(mcpDao: McpDao) {
        val allServers = mcpDao.getEnabledServersSync()
        val allTools = mutableListOf<McpToolEntity>()
        
        for (server in allServers.filter { it.enabled }) {
            allTools.addAll(mcpDao.getToolsForServerSync(server.id))
        }

        val nameCounts = allTools.groupingBy { it.name }.eachCount()
        
        val updates = allTools.mapNotNull { t ->
            val count = nameCounts[t.name] ?: 0
            if (count > 1) {
                if (t.enabled || t.status == "active") {
                    t.copy(
                        enabled = false,
                        status = "disabled",
                        errorMessage = "Duplicate name across servers or within server"
                    )
                } else null
            } else if (count == 1 && t.errorMessage == "Duplicate name across servers or within server") {
                // Recover if it is now unique
                t.copy(
                    enabled = true,
                    status = "active",
                    errorMessage = null
                )
            } else {
                null
            }
        }
        
        if (updates.isNotEmpty()) {
            mcpDao.insertTools(updates) // replace in DB
        }
    }
}
