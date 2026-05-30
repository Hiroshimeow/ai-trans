package com.example.mcp

import com.example.data.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class McpClientImpl(
    private val defaultClient: OkHttpClient,
    private val credentialStore: CredentialStore
) : McpClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getClientForServer(server: McpServerConfig): OkHttpClient {
        return defaultClient.newBuilder()
            .callTimeout(server.timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun getAuthToken(server: McpServerConfig): String {
        return credentialStore.getSecret(server.tokenAlias) ?: ""
    }

    override suspend fun listTools(server: McpServerConfig): List<McpToolDefinition> = withContext(Dispatchers.IO) {
        val client = getClientForServer(server)
        val token = getAuthToken(server)
        
        // MCP standard JSON-RPC payload for tools/list
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", UUID.randomUUID().toString())
            put("method", "tools/list")
            put("params", JSONObject())
        }

        val request = Request.Builder()
            .url(server.baseUrl) // Standard MCP HTTP POST endpoint
            .post(payload.toString().toRequestBody(jsonMediaType))
            .apply {
                if (token.isNotEmpty()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to list tools: HTTP ${response.code} ${response.message}")
            }
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(responseBody)
            
            if (json.has("error")) {
                throw Exception("MCP Error: ${json.getJSONObject("error").optString("message")}")
            }

            val resultParams = json.optJSONObject("result") ?: return@withContext emptyList()
            val toolsArray = resultParams.optJSONArray("tools") ?: return@withContext emptyList()
            
            val definitions = mutableListOf<McpToolDefinition>()
            for (i in 0 until toolsArray.length()) {
                val toolObj = toolsArray.getJSONObject(i)
                definitions.add(
                    McpToolDefinition(
                        name = toolObj.getString("name"),
                        description = toolObj.optString("description", ""),
                        inputSchema = toolObj.getJSONObject("inputSchema").toString()
                    )
                )
            }
            definitions
        }
    }

    override suspend fun callTool(
        server: McpServerConfig,
        name: String,
        argumentsJson: String
    ): McpToolResult = withContext(Dispatchers.IO) {
        val client = getClientForServer(server)
        val token = getAuthToken(server)

        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", UUID.randomUUID().toString())
            put("method", "tools/call")
            val params = JSONObject()
            params.put("name", name)
            params.put("arguments", JSONObject(argumentsJson))
            put("params", params)
        }

        val request = Request.Builder()
            .url(server.baseUrl)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .apply {
                if (token.isNotEmpty()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext McpToolResult(true, "HTTP ${response.code}: ${response.message}")
            }
            val responseBody = response.body?.string() ?: return@withContext McpToolResult(true, "Empty response")
            val json = JSONObject(responseBody)
            
            if (json.has("error")) {
                val errorObj = json.getJSONObject("error")
                return@withContext McpToolResult(true, errorObj.optString("message", "Unknown MCP error"))
            }

            val resultParams = json.optJSONObject("result")
            if (resultParams != null) {
                val isError = resultParams.optBoolean("isError", false)
                val contentArray = resultParams.optJSONArray("content")
                // Format the content array nicely as a string result for the AI
                val textOutput = if (contentArray != null && contentArray.length() > 0) {
                    val sb = java.lang.StringBuilder()
                    for (i in 0 until contentArray.length()) {
                        val c = contentArray.getJSONObject(i)
                        if (c.optString("type") == "text") {
                            sb.append(c.optString("text")).append("\n")
                        }
                    }
                    sb.toString().trim()
                } else {
                    resultParams.toString()
                }
                return@withContext McpToolResult(isError, textOutput)
            }
            
            McpToolResult(false, "Success (no content)")
        }
    }
}
