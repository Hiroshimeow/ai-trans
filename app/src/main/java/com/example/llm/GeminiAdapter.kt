package com.example.llm

import android.util.Base64
import android.util.Log
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class GeminiAdapter(
    private val apiKey: String,
    private val mcpRepository: com.example.mcp.McpRepository
) : LlmAdapter {
    private val tag = "GeminiAdapter"

    override val capabilities = ProviderCapabilities(
        text = true,
        imageInline = true,
        pdfInline = true,
        audioInline = true,
        toolCalling = true
    )

    override suspend fun sendChat(request: CoreChatRequest): String = withContext(Dispatchers.IO) {
        val contents = mutableListOf<Content>()

        // 1. Chat History
        request.chatHistory.forEach { msg ->
            if (!msg.isError && !msg.isPending) {
                contents.add(Content(role = msg.role, parts = listOf(Part(text = msg.content))))
            }
        }

        // 2. User Message & Attachments
        val currentUserParts = mutableListOf<Part>()
        currentUserParts.add(Part(text = request.userMessage.content))

        request.attachments.forEach { attach ->
            when (attach.type) {
                AttachmentType.IMAGE -> {
                    currentUserParts.add(Part(inlineData = InlineData(mimeType = attach.attachment.mimeType, data = attach.base64Data!!)))
                }
                AttachmentType.PDF -> {
                    currentUserParts.add(Part(inlineData = InlineData(mimeType = "application/pdf", data = attach.base64Data!!)))
                }
                AttachmentType.AUDIO -> {
                    currentUserParts.add(Part(inlineData = InlineData(mimeType = attach.attachment.mimeType, data = attach.base64Data!!)))
                }
                AttachmentType.TEXT -> {
                    currentUserParts.add(Part(text = "\n[Attached File: \${attach.attachment.displayName}]\n\${attach.textContent}\n[End of File]\n"))
                }
                AttachmentType.UNKNOWN -> {}
            }
        }

        contents.add(Content(role = "user", parts = currentUserParts))

        // 3. System Instructions
        val systemInstruction = if (request.systemPrompt.isNotEmpty()) {
            Content(parts = listOf(Part(text = request.systemPrompt)))
        } else {
            null
        }

        // 4. Resolve MCP Tools
        val mcpToolsToRun = mutableListOf<FunctionDeclaration>()
        val toolIdToMcpMeta = mutableMapOf<String, com.example.data.McpToolEntity>()
        val enabledServers = mcpRepository.getEnabledServersSync()
        
        for (server in enabledServers) {
            val tools = mcpRepository.getEnabledToolsForServer(server.id)
            for (t in tools) {
                try {
                    val schemaObj = org.json.JSONObject(t.inputSchemaJson)
                    val parametersMap = jsonObjectToMap(schemaObj)
                    val safeName = t.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val desc = if (t.description.isNotBlank()) t.description else safeName
                    
                    val functionDeclaration = FunctionDeclaration(
                        name = safeName,
                        description = desc,
                        parameters = parametersMap
                    )
                    mcpToolsToRun.add(functionDeclaration)
                    toolIdToMcpMeta[safeName] = t
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse tool schema for \${t.name}", e)
                }
            }
        }

        val declaredTools = if (mcpToolsToRun.isNotEmpty()) listOf(Tool(functionDeclarations = mcpToolsToRun)) else null

        // 5. Tool Calling Loop
        var apiRequest = GenerateContentRequest(
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = GenerationConfig(temperature = 0.4f),
            tools = declaredTools
        )

        var maxIterations = 5
        while (maxIterations > 0) {
            maxIterations--
            val response = GeminiRetrofitClient.service.generateContent(request.modelToUse, apiKey, apiRequest)
            
            if (response.error != null) {
                throw IOException("Gemini API Error: \${response.error.message}")
            }
            
            val candidate = response.candidates?.firstOrNull() ?: throw IOException("Gemini API Error: empty candidates list")
            
            val functionCalls = candidate.content?.parts?.filter { it.functionCall != null }?.map { it.functionCall!! }
            if (functionCalls.isNullOrEmpty()) {
                val responseText = candidate.content?.parts?.firstOrNull { it.text != null }?.text
                return@withContext responseText ?: ""
            }
            
            // Execute and create proper function responses
            val newContents = apiRequest.contents.toMutableList()
            newContents.add(candidate.content)
            
            val userResponseParts = mutableListOf<Part>()
            for (call in functionCalls) {
                val toolName = call.name
                val argsObj = call.args ?: emptyMap<String, Any>()
                val argsJsonStr = org.json.JSONObject(argsObj).toString()
                
                val mcpMeta = toolIdToMcpMeta[toolName]
                val resultData: Map<String, Any> = if (mcpMeta != null) {
                    try {
                        val result = mcpRepository.executeTool(mcpMeta.serverId, mcpMeta.name, argsJsonStr)
                        mapOf("result" to result.content)
                    } catch (e: Exception) {
                        mapOf("error" to "Failed to execute tool: \${e.message}")
                    }
                } else {
                    mapOf("error" to "Tool \$toolName not configured")
                }
                
                userResponseParts.add(Part(functionResponse = FunctionResponse(name = toolName, response = resultData)))
            }
            
            newContents.add(Content(role = "user", parts = userResponseParts))
            apiRequest = apiRequest.copy(contents = newContents)
        }
        
        throw IOException("Exceeded maximum tool-call iterations without returning final text")
    }

    override suspend fun transcribeAudio(audioBytes: ByteArray, promptText: String): String = withContext(Dispatchers.IO) {
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(inlineData = InlineData(mimeType = "audio/wav", data = base64Audio)),
                        Part(text = promptText)
                    )
                )
            ),
            generationConfig = GenerationConfig(temperature = 0.1f)
        )

        val response = GeminiRetrofitClient.service.generateContent("gemini-1.5-flash", apiKey, request)
        return@withContext response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() 
            ?: throw IOException("Empty transcript from Gemini")
    }

    override suspend fun polishAudioAndTxt(audioBytes: ByteArray?, rawSTT: String): String = withContext(Dispatchers.IO) {
        val promptText = """
Bạn là Trợ lý AI chuyên nghiệp về hiệu đính và tối ưu hóa tài liệu/biên bản cuộc họp.
Dưới đây là một file âm thanh ghi âm cuộc họp, đi kèm với nội dung chuyển ngữ thô (raw STT) được ghi lại theo thời gian thực trên thiết bị.
Hãy kết hợp cả hai nguồn thông tin này để hiệu chỉnh chính tả, sửa các từ sai ngữ nghĩa do âm sắc địa phương hoặc nhiễu tạp âm, thêm dấu câu, chia đoạn và định dạng thành một Biên bản họp hoàn chỉnh đầy đủ, rõ ràng và mượt mà bằng tiếng Việt.

NỘI DUNG CHUYỂN NGỮ THÔ (RAW STT):
\$rawSTT
"""
        val parts = mutableListOf<Part>()
        if (audioBytes != null && audioBytes.isNotEmpty()) {
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            parts.add(Part(inlineData = InlineData(mimeType = "audio/wav", data = base64Audio)))
        }
        parts.add(Part(text = promptText))

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(temperature = 0.2f)
        )

        val response = GeminiRetrofitClient.service.generateContent("gemini-2.5-flash", apiKey, request)
        return@withContext response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() 
            ?: throw IOException("Polishing failed. No feedback from Gemini service.")
    }

    private fun jsonObjectToMap(jsonObject: org.json.JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)
            if (value is org.json.JSONObject) {
                map[key] = jsonObjectToMap(value)
            } else if (value is org.json.JSONArray) {
                map[key] = jsonArrayToList(value)
            } else {
                map[key] = value
            }
        }
        return map
    }

    private fun jsonArrayToList(array: org.json.JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            if (value is org.json.JSONObject) {
                list.add(jsonObjectToMap(value))
            } else if (value is org.json.JSONArray) {
                list.add(jsonArrayToList(value))
            } else {
                list.add(value)
            }
        }
        return list
    }
}
