package com.example.data

import org.json.JSONArray
import org.json.JSONObject

data class LlmProvider(
    val id: String,
    val name: String,
    val endpointUrl: String,
    val apiKey: String,
    val models: List<String>,
    val maxTokens: Int = 4096
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("endpointUrl", endpointUrl)
        obj.put("apiKey", SecurityHelper.encrypt(apiKey))
        
        val modelsArr = JSONArray()
        models.forEach { modelsArr.put(it) }
        obj.put("models", modelsArr)
        
        obj.put("maxTokens", maxTokens)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): LlmProvider {
            val id = obj.optString("id", "")
            val name = obj.optString("name", "")
            val endpointUrl = obj.optString("endpointUrl", "")
            val apiKey = SecurityHelper.decrypt(obj.optString("apiKey", ""))
            
            val models = mutableListOf<String>()
            val modelsArr = obj.optJSONArray("models")
            if (modelsArr != null) {
                for (i in 0 until modelsArr.length()) {
                    val m = modelsArr.optString(i)
                    if (!m.isNullOrBlank()) {
                        models.add(m)
                    }
                }
            }
            val maxTokens = obj.optInt("maxTokens", 4096)
            return LlmProvider(id, name, endpointUrl, apiKey, models, maxTokens)
        }

        fun getDefaultProviders(): List<LlmProvider> {
            return listOf(
                LlmProvider(
                    id = "gemini",
                    name = "Gemini",
                    endpointUrl = "",
                    apiKey = "",
                    models = listOf(
                        "gemini-2.5-flash",
                        "gemini-2.5-pro",
                        "gemini-1.5-flash",
                        "gemini-1.5-pro"
                    ),
                    maxTokens = 4096
                )
            )
        }

        fun toJsonArrayString(providers: List<LlmProvider>): String {
            val arr = JSONArray()
            providers.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }

        fun fromJsonArrayString(jsonStr: String): List<LlmProvider> {
            if (jsonStr.isBlank()) return getDefaultProviders()
            return try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<LlmProvider>()
                for (i in 0 until arr.length()) {
                    list.add(fromJsonObject(arr.getJSONObject(i)))
                }
                if (list.isEmpty()) {
                    getDefaultProviders()
                } else {
                    list
                }
            } catch (e: Exception) {
                getDefaultProviders()
            }
        }
    }
}
