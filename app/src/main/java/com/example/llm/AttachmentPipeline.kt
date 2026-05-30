package com.example.llm

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.AttachmentEntity

class AttachmentPipeline(private val context: Context) {
    private val tag = "AttachmentPipeline"

    fun processAttachments(attachments: List<AttachmentEntity>): List<ProcessedAttachment> {
        return attachments.filter { it.status == "selected" }.mapNotNull { processSingle(it) }
    }

    private fun processSingle(attach: AttachmentEntity): ProcessedAttachment? {
        val type = determineType(attach)
        
        return try {
            val uri = Uri.parse(attach.uri)
            when (type) {
                AttachmentType.IMAGE, AttachmentType.PDF, AttachmentType.AUDIO -> {
                    val base64 = loadBase64(uri)
                    if (base64 != null) ProcessedAttachment(attach, type, base64Data = base64) else null
                }
                AttachmentType.TEXT -> {
                    val text = loadText(uri)
                    if (text != null) ProcessedAttachment(attach, type, textContent = text) else null
                }
                AttachmentType.UNKNOWN -> null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to process attachment \${attach.displayName}", e)
            null
        }
    }

    private fun determineType(attach: AttachmentEntity): AttachmentType {
        val mime = attach.mimeType.lowercase()
        if (mime.startsWith("image/")) return AttachmentType.IMAGE
        if (mime == "application/pdf" || attach.displayName.lowercase().endsWith(".pdf")) return AttachmentType.PDF
        if (mime.startsWith("audio/")) return AttachmentType.AUDIO
        
        if (mime.startsWith("text/")) return AttachmentType.TEXT
        val textMimes = setOf("application/json", "application/xml", "application/javascript", "application/x-javascript")
        if (mime in textMimes) return AttachmentType.TEXT
        
        val ext = attach.displayName.substringAfterLast('.', "").lowercase()
        val textExts = setOf("txt", "md", "markdown", "json", "xml", "csv", "kt", "java", "py", "js", "ts", "html", "css", "yaml", "yml", "gradle")
        if (ext in textExts) return AttachmentType.TEXT
        
        return AttachmentType.UNKNOWN
    }

    private fun loadBase64(uri: Uri): String? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    private fun loadText(uri: Uri): String? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        }
    }
}
