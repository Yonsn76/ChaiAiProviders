package com.yonsn76.freeaiconnect.providers

import android.os.Handler
import android.os.Looper
import com.yonsn76.freeaiconnect.models.ChatMessage

abstract class AIProvider {
    abstract val name: String
    abstract val models: List<String>
    abstract val requiresApiKey: Boolean

    /**
     * Sends a message to the AI provider in a background thread.
     * Callbacks are invoked on the main thread.
     *
     * @param messages The conversation history to send.
     * @param model The model identifier to use.
     * @param apiKey The API key for authentication.
     * @param temperature Controls randomness (0.0 - 1.0).
     * @param maxTokens Maximum tokens in the response.
     * @param systemPrompt Optional system prompt to prepend.
     * @param onToken Called per token with the FULL accumulated text so far (main thread).
     * @param onComplete Called with the complete final response (main thread).
     * @param onError Called with an error message if something goes wrong (main thread).
     */
    abstract fun sendMessage(
        messages: List<ChatMessage>,
        model: String,
        apiKey: String,
        temperature: Float,
        maxTokens: Int,
        systemPrompt: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    )

    protected val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        fun getAllProviders(): List<AIProvider> = listOf(
            OpenRouterProvider(),
            GoogleProvider(),
            OpenAIProvider(),
            MistralProvider()
        )

        fun getAllProviders(context: android.content.Context): List<AIProvider> {
            val prefs = com.yonsn76.freeaiconnect.storage.PrefsManager(context)
            val customProviders = prefs.getCustomProviders()
            return getAllProviders() + customProviders
        }
    }
}
