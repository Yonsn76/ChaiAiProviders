package com.yonsn76.freeaiconnect.storage

import android.content.Context
import androidx.core.content.edit
import com.yonsn76.freeaiconnect.models.Conversation
import org.json.JSONObject

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("freeaiconnect_prefs", Context.MODE_PRIVATE)

    // ── API Keys ────────────────────────────────────────────────
    fun getApiKey(provider: String): String =
        prefs.getString("api_key_${provider.lowercase()}", "") ?: ""

    fun setApiKey(provider: String, key: String) =
        prefs.edit { putString("api_key_${provider.lowercase()}", key) }

    // ── Selected provider / model indices ───────────────────────
    fun getSelectedProviderIndex(): Int = prefs.getInt("selected_provider", 0)
    fun setSelectedProviderIndex(index: Int) =
        prefs.edit { putInt("selected_provider", index) }

    fun getSelectedModelIndex(): Int = prefs.getInt("selected_model", 0)
    fun setSelectedModelIndex(index: Int) =
        prefs.edit { putInt("selected_model", index) }

    // ── Temperature (stored as int 0-100, exposed as float 0.0-1.0) ──
    fun getTemperature(): Float = prefs.getInt("temperature", 70) / 100f
    fun setTemperature(temp: Float) =
        prefs.edit { putInt("temperature", (temp * 100).toInt()) }

    fun getTemperatureRaw(): Int = prefs.getInt("temperature", 70)

    // ── Max tokens ──────────────────────────────────────────────
    fun getMaxTokens(): Int = prefs.getInt("max_tokens", 2048)
    fun setMaxTokens(tokens: Int) =
        prefs.edit { putInt("max_tokens", tokens) }

    // ── System prompt ───────────────────────────────────────────
    fun getSystemPrompt(): String =
        prefs.getString("system_prompt", "") ?: ""

    fun setSystemPrompt(prompt: String) =
        prefs.edit { putString("system_prompt", prompt) }

    // ── Conversations ───────────────────────────────────────────
    fun saveConversation(conversation: Conversation) {
        prefs.edit { putString("conv_${conversation.id}", conversation.toJSON().toString()) }
        // Update the conversation IDs set
        val ids = getConversationIds().toMutableSet()
        ids.add(conversation.id)
        prefs.edit { putStringSet("conversation_ids", ids) }
    }

    fun loadConversation(id: String): Conversation? {
        val json = prefs.getString("conv_$id", null) ?: return null
        return try {
            Conversation.fromJSON(JSONObject(json))
        } catch (e: Exception) {
            null
        }
    }

    fun deleteConversation(id: String) {
        prefs.edit { remove("conv_$id") }
        val ids = getConversationIds().toMutableSet()
        ids.remove(id)
        prefs.edit { putStringSet("conversation_ids", ids) }
    }

    fun getAllConversations(): List<Conversation> {
        return getConversationIds()
            .mapNotNull { loadConversation(it) }
            .sortedByDescending { it.updatedAt }
    }

    private fun getConversationIds(): Set<String> =
        prefs.getStringSet("conversation_ids", emptySet()) ?: emptySet()

    // ── Active conversation ID ──────────────────────────────────
    fun getActiveConversationId(): String? =
        prefs.getString("active_conversation_id", null)

    fun setActiveConversationId(id: String?) =
        prefs.edit { putString("active_conversation_id", id) }
}
