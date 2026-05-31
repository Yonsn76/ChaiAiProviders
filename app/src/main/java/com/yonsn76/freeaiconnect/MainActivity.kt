package com.yonsn76.freeaiconnect

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yonsn76.freeaiconnect.adapters.ChatAdapter
import com.yonsn76.freeaiconnect.adapters.ConversationAdapter
import com.yonsn76.freeaiconnect.models.ChatMessage
import com.yonsn76.freeaiconnect.models.Conversation
import com.yonsn76.freeaiconnect.providers.AIProvider
import com.yonsn76.freeaiconnect.storage.PrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rvMessages: RecyclerView
    private lateinit var rvConversations: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnNewChat: Button
    private lateinit var tvModelName: TextView
    private lateinit var emptyState: View
    private lateinit var modelPillContainer: View

    private lateinit var prefsManager: PrefsManager
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var conversationAdapter: ConversationAdapter

    private var currentConversation: Conversation? = null
    private var isStreaming = false
    private val providers: List<AIProvider> get() = AIProvider.getAllProviders(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefsManager = PrefsManager(this)
        initViews()
        setupRecyclerViews()
        setupClickListeners()
        setupInput()
        setupBackHandling()
        loadLastConversation()
        updateModelDisplay()
    }

    // ── View initialization ─────────────────────────────────────

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        rvMessages = findViewById(R.id.rv_messages)
        rvConversations = findViewById(R.id.rv_conversations)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        btnMenu = findViewById(R.id.btn_menu)
        btnSettings = findViewById(R.id.btn_settings)
        btnNewChat = findViewById(R.id.btn_new_chat)
        tvModelName = findViewById(R.id.tv_model_name)
        emptyState = findViewById(R.id.empty_state)
        modelPillContainer = findViewById(R.id.model_pill_container)
    }

    // ── RecyclerView setup ──────────────────────────────────────

    private fun setupRecyclerViews() {
        // Chat messages
        chatAdapter = ChatAdapter(this)
        rvMessages.adapter = chatAdapter
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        // Conversation list in drawer
        conversationAdapter = ConversationAdapter(
            this,
            onConversationClick = { conv -> switchConversation(conv) },
            onDeleteClick = { conv -> deleteConversation(conv) }
        )
        rvConversations.adapter = conversationAdapter
        rvConversations.layoutManager = LinearLayoutManager(this)
        refreshConversationList()
    }

    // ── Click listeners ─────────────────────────────────────────

    private fun setupClickListeners() {
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        btnSettings.setOnClickListener { openSettings() }
        modelPillContainer.setOnClickListener { openSettings() }

        btnNewChat.setOnClickListener { createNewConversation() }

        btnSend.setOnClickListener { sendMessage() }
    }

    // ── Input handling ──────────────────────────────────────────

    private fun setupInput() {
        etMessage.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER &&
                !event.isShiftPressed &&
                event.action == KeyEvent.ACTION_DOWN
            ) {
                sendMessage()
                return@setOnKeyListener true
            }
            false
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    // ── Sending a message ───────────────────────────────────────

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty() || isStreaming) return

        val providerIndex = prefsManager.getSelectedProviderIndex()
        val modelIndex = prefsManager.getSelectedModelIndex()
        val provider = providers.getOrNull(providerIndex) ?: providers[0]
        val allModels = getModelsForProvider(provider)
        val model = allModels.getOrNull(modelIndex) ?: allModels.firstOrNull() ?: provider.models[0]
        val apiKey = prefsManager.getApiKey(provider.name)

        if (provider.requiresApiKey && apiKey.isBlank()) {
            Toast.makeText(
                this,
                "Configure your ${provider.name} API key in Settings",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Create conversation if needed
        if (currentConversation == null) {
            createNewConversation()
        }

        val conv = currentConversation!!

        // Add user message
        val userMsg = ChatMessage(
            role = "user",
            content = text,
            provider = provider.name,
            model = model
        )
        conv.messages.add(userMsg)
        chatAdapter.addMessage(userMsg)

        // Auto-title from first user message
        if (conv.messages.count { it.role == "user" } == 1) {
            conv.title = text.take(40) + if (text.length > 40) "..." else ""
        }

        etMessage.setText("")
        updateEmptyState()
        scrollToBottom()

        // Add placeholder AI message for streaming
        val aiMsg = ChatMessage(
            role = "assistant",
            content = "",
            provider = provider.name,
            model = model
        )
        conv.messages.add(aiMsg)
        chatAdapter.addMessage(aiMsg)

        isStreaming = true
        btnSend.isEnabled = false

        // Build the messages list to send to the API.
        // Only valid roles: system, user, assistant.
        // "error" messages are UI-only and must never be sent.
        // The last message must always be "user" (or "tool").
        val validRoles = setOf("user", "assistant")
        val messagesToSend = conv.messages
            .filter { it.role in validRoles && it.content.isNotBlank() }
            // filter already excludes the empty AI placeholder (content is blank)
            .let { list ->
                // If the last message is assistant, trim until we find a user message
                val trimmed = list.toMutableList()
                while (trimmed.isNotEmpty() && trimmed.last().role == "assistant") {
                    trimmed.removeAt(trimmed.size - 1)
                }
                trimmed
            }

        provider.sendMessage(
            messages = messagesToSend,
            model = model,
            apiKey = apiKey,
            temperature = prefsManager.getTemperature(),
            maxTokens = prefsManager.getMaxTokens(),
            systemPrompt = prefsManager.getSystemPrompt(),
            onToken = { fullText ->
                aiMsg.content = fullText
                chatAdapter.updateLastAiMessage(fullText)
                scrollToBottom()
            },
            onComplete = { fullText ->
                aiMsg.content = fullText
                chatAdapter.updateLastAiMessage(fullText)
                isStreaming = false
                btnSend.isEnabled = true
                conv.updatedAt = System.currentTimeMillis()
                prefsManager.saveConversation(conv)
                refreshConversationList()
                scrollToBottom()
            },
            onError = { error ->
                if (conv.messages.isNotEmpty() && conv.messages.last().role == "assistant") {
                    conv.messages.removeAt(conv.messages.size - 1)
                }
                chatAdapter.setMessages(conv.messages)

                val errorMsg = ChatMessage(
                    role = "error",
                    content = error,
                    provider = provider.name,
                    model = model
                )
                conv.messages.add(errorMsg)
                chatAdapter.addMessage(errorMsg)

                isStreaming = false
                btnSend.isEnabled = true
                scrollToBottom()
                prefsManager.saveConversation(conv)
            }
        )
    }

    // ── Conversation management ─────────────────────────────────

    private fun createNewConversation() {
        val conv = Conversation()
        currentConversation = conv
        prefsManager.saveConversation(conv)
        prefsManager.setActiveConversationId(conv.id)
        chatAdapter.setMessages(emptyList())
        updateEmptyState()
        refreshConversationList()
        drawerLayout.closeDrawers()
    }

    private fun switchConversation(conv: Conversation) {
        currentConversation = conv
        prefsManager.setActiveConversationId(conv.id)
        chatAdapter.setMessages(conv.messages)
        updateEmptyState()
        conversationAdapter.activeConversationId = conv.id
        conversationAdapter.notifyDataSetChanged()
        drawerLayout.closeDrawers()
        scrollToBottom()
    }

    private fun deleteConversation(conv: Conversation) {
        prefsManager.deleteConversation(conv.id)
        if (currentConversation?.id == conv.id) {
            currentConversation = null
            prefsManager.setActiveConversationId(null)
            chatAdapter.setMessages(emptyList())
            updateEmptyState()
        }
        refreshConversationList()
    }

    private fun loadLastConversation() {
        val activeId = prefsManager.getActiveConversationId()
        if (activeId != null) {
            val conv = prefsManager.loadConversation(activeId)
            if (conv != null) {
                currentConversation = conv
                chatAdapter.setMessages(conv.messages)
            }
        }
        updateEmptyState()
    }

    private fun refreshConversationList() {
        val convs = prefsManager.getAllConversations()
        conversationAdapter.activeConversationId = currentConversation?.id
        conversationAdapter.setConversations(convs)
    }

    // ── UI helpers ──────────────────────────────────────────────

    private fun updateEmptyState() {
        val hasMessages = currentConversation?.messages?.isNotEmpty() == true
        emptyState.visibility = if (hasMessages) View.GONE else View.VISIBLE
        rvMessages.visibility = if (hasMessages) View.VISIBLE else View.INVISIBLE
    }

    private fun getModelsForProvider(provider: AIProvider): List<String> {
        val custom = prefsManager.getCustomModels(provider.name)
        return (provider.models + custom.filter { it !in provider.models })
    }

    private fun updateModelDisplay() {
        val providerIndex = prefsManager.getSelectedProviderIndex()
        val modelIndex = prefsManager.getSelectedModelIndex()
        val provider = providers.getOrNull(providerIndex) ?: providers[0]
        val allModels = getModelsForProvider(provider)
        val model = allModels.getOrNull(modelIndex) ?: allModels.firstOrNull() ?: ""
        tvModelName.text = model
    }

    private fun openSettings() {
        val sheet = SettingsBottomSheet()
        sheet.onSettingsSaved = {
            updateModelDisplay()
        }
        sheet.show(supportFragmentManager, "settings")
    }

    private fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            rvMessages.scrollToPosition(chatAdapter.itemCount - 1)
        }
    }
}
