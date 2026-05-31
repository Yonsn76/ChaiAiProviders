package com.yonsn76.freeaiconnect.providers

import com.yonsn76.freeaiconnect.models.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

abstract class OpenAICompatibleProvider : AIProvider() {
    abstract val baseUrl: String
    override val requiresApiKey = true

    override fun sendMessage(
        messages: List<ChatMessage>,
        model: String,
        apiKey: String,
        temperature: Float,
        maxTokens: Int,
        systemPrompt: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val url = URL(baseUrl)
                val conn = url.openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Accept", "text/event-stream")
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.doOutput = true
                conn.doInput = true

                // Build request body JSON
                val messagesArray = JSONArray()
                if (systemPrompt.isNotBlank()) {
                    messagesArray.put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", systemPrompt)
                    )
                }
                for (msg in messages) {
                    messagesArray.put(
                        JSONObject()
                            .put("role", msg.role)
                            .put("content", msg.content)
                    )
                }

                val body = JSONObject()
                body.put("model", model)
                body.put("messages", messagesArray)
                body.put("stream", true)
                body.put("temperature", temperature.toDouble())
                body.put("max_tokens", maxTokens)

                conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

                if (conn.responseCode !in 200..299) {
                    val errorBody = try {
                        conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    } catch (e: Exception) {
                        "Unknown error (${conn.responseCode})"
                    }
                    mainHandler.post { onError("HTTP ${conn.responseCode}: $errorBody") }
                    return@Thread
                }

                // Read SSE stream
                val fullResponse = StringBuilder()
                conn.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        if (l.startsWith("data: ")) {
                            val data = l.substring(6).trim()
                            if (data == "[DONE]") break
                            try {
                                val json = JSONObject(data)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    val content = delta?.optString("content", "") ?: ""
                                    if (content.isNotEmpty()) {
                                        fullResponse.append(content)
                                        val currentFull = fullResponse.toString()
                                        mainHandler.post { onToken(currentFull) }
                                    }
                                }
                            } catch (e: Exception) {
                                // Skip malformed SSE lines
                            }
                        }
                    }
                }

                val finalResponse = fullResponse.toString()
                mainHandler.post { onComplete(finalResponse) }
            } catch (e: Exception) {
                mainHandler.post { onError(e.message ?: "Connection error") }
            }
        }.start()
    }
}
