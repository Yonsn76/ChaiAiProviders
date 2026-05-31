package com.yonsn76.freeaiconnect.models

import org.json.JSONObject

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "system"
    var content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String = "",
    val model: String = ""
) {
    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("role", role)
        json.put("content", content)
        json.put("timestamp", timestamp)
        json.put("provider", provider)
        json.put("model", model)
        return json
    }

    companion object {
        fun fromJSON(json: JSONObject): ChatMessage {
            return ChatMessage(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                role = json.optString("role", "user"),
                content = json.optString("content", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                provider = json.optString("provider", ""),
                model = json.optString("model", "")
            )
        }
    }
}
