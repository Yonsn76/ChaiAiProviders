package com.yonsn76.freeaiconnect.providers

import com.yonsn76.freeaiconnect.models.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class GoogleProvider : AIProvider() {
    override val name = "Google"
    override val requiresApiKey = true
    override val models = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.0-flash"
    )

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
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
                )
                val conn = url.openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.doOutput = true
                conn.doInput = true

                // Build Gemini request body
                val contents = JSONArray()
                for (msg in messages) {
                    val role = if (msg.role == "assistant") "model" else "user"
                    val parts = JSONArray().put(JSONObject().put("text", msg.content))
                    contents.put(JSONObject().put("role", role).put("parts", parts))
                }

                val body = JSONObject()
                body.put("contents", contents)

                // System instruction (separate field in Gemini API)
                if (systemPrompt.isNotBlank()) {
                    val systemInstruction = JSONObject()
                    systemInstruction.put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemPrompt))
                    )
                    body.put("systemInstruction", systemInstruction)
                }

                // Generation config
                val genConfig = JSONObject()
                genConfig.put("temperature", temperature.toDouble())
                genConfig.put("maxOutputTokens", maxTokens)
                body.put("generationConfig", genConfig)

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

                // Read SSE stream (Gemini format)
                val fullResponse = StringBuilder()
                conn.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        if (l.startsWith("data: ")) {
                            val data = l.substring(6).trim()
                            try {
                                val json = JSONObject(data)
                                val candidates = json.optJSONArray("candidates")
                                if (candidates != null && candidates.length() > 0) {
                                    val content = candidates.getJSONObject(0)
                                        .optJSONObject("content")
                                        ?.optJSONArray("parts")
                                        ?.optJSONObject(0)
                                        ?.optString("text", "") ?: ""
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
