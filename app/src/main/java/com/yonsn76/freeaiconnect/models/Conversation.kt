package com.yonsn76.freeaiconnect.models

import org.json.JSONArray
import org.json.JSONObject

data class Conversation(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "New Conversation",
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("title", title)
        json.put("createdAt", createdAt)
        json.put("updatedAt", updatedAt)
        val messagesArray = JSONArray()
        for (message in messages) {
            messagesArray.put(message.toJSON())
        }
        json.put("messages", messagesArray)
        return json
    }

    companion object {
        fun fromJSON(json: JSONObject): Conversation {
            val messagesArray = json.optJSONArray("messages") ?: JSONArray()
            val messagesList = mutableListOf<ChatMessage>()
            for (i in 0 until messagesArray.length()) {
                try {
                    messagesList.add(ChatMessage.fromJSON(messagesArray.getJSONObject(i)))
                } catch (e: Exception) {
                    // Skip malformed messages
                }
            }
            return Conversation(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                title = json.optString("title", "New Conversation"),
                messages = messagesList,
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}
