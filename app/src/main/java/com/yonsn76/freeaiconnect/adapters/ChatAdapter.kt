package com.yonsn76.freeaiconnect.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yonsn76.freeaiconnect.R
import com.yonsn76.freeaiconnect.models.ChatMessage
import com.yonsn76.freeaiconnect.models.MarkdownBlock
import com.yonsn76.freeaiconnect.utils.MarkdownUtils

class ChatAdapter(private val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    companion object {
        const val TYPE_USER = 0
        const val TYPE_AI = 1
        const val TYPE_ERROR = 2

        private val HEADING_PATTERN = Regex("^(#{1,6})\\s*(.+)$")
        private val LIST_ITEM_PATTERN = Regex("^\\s*((?:\\d+\\.)|[-*])\\s+(.+)$")
    }

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tv_message_user)
    }

    inner class AiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val llContent: LinearLayout = view.findViewById(R.id.ll_ai_content)
    }

    inner class ErrorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tv_message_error)
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.role == "error" -> TYPE_ERROR
            msg.role == "user" -> TYPE_USER
            else -> TYPE_AI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_USER -> {
                val view = LayoutInflater.from(context)
                    .inflate(R.layout.item_message_user, parent, false)
                UserViewHolder(view)
            }
            TYPE_ERROR -> {
                val view = LayoutInflater.from(context)
                    .inflate(R.layout.item_message_error, parent, false)
                ErrorViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(context)
                    .inflate(R.layout.item_message_ai, parent, false)
                AiViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is UserViewHolder -> {
                holder.tvMessage.text = msg.content
            }
            is AiViewHolder -> {
                holder.llContent.removeAllViews()
                renderAiMessage(holder.llContent, msg.content, msg.model)
            }
            is ErrorViewHolder -> {
                holder.tvMessage.text = msg.content
            }
        }
    }

    private fun renderAiMessage(container: LinearLayout, content: String, model: String) {
        if (content.isEmpty()) return

        val blocks = runCatching { MarkdownUtils.parseBlocks(content) }.getOrDefault(
            listOf(MarkdownBlock.Text(content))
        )

        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Text -> {
                    renderTextBlock(container, block.content)
                }
                is MarkdownBlock.Code -> {
                    val codeView = LayoutInflater.from(context)
                        .inflate(R.layout.item_code_block, container, false)

                    val tvLang = codeView.findViewById<TextView>(R.id.tv_code_language)
                    val tvCode = codeView.findViewById<TextView>(R.id.tv_code_content)
                    val btnCopy = codeView.findViewById<ImageButton>(R.id.btn_copy_code)

                    tvLang.text = block.language

                    val highlighted = runCatching {
                        MarkdownUtils.highlightCode(block.code, block.language, context)
                    }.getOrDefault(android.text.SpannableString(block.code))
                    tvCode.text = highlighted

                    btnCopy.setOnClickListener {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("code", block.code))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }

                    container.addView(codeView)
                }
            }
        }

        // Model label
        if (model.isNotBlank()) {
            val tvModel = TextView(context).apply {
                text = model
                textSize = 10f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                typeface = android.graphics.Typeface.MONOSPACE
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(4, 4, 0, 0)
                layoutParams = params
            }
            container.addView(tvModel)
        }
    }

    private fun renderTextBlock(container: LinearLayout, rawContent: String) {
        val content = MarkdownUtils.cleanupText(rawContent)
        if (content.isEmpty()) return

        val paragraph = mutableListOf<String>()
        var hasRendered = false

        fun flushParagraph() {
            val text = paragraph.joinToString(" ").trim()
            if (text.isNotEmpty()) {
                addTextView(
                    container = container,
                    text = text,
                    topMargin = if (hasRendered) 10 else 0,
                    bottomMargin = 4
                )
                hasRendered = true
            }
            paragraph.clear()
        }

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushParagraph()
                continue
            }

            val heading = HEADING_PATTERN.matchEntire(trimmed)
            if (heading != null) {
                flushParagraph()
                val level = heading.groupValues[1].length
                addTextView(
                    container = container,
                    text = heading.groupValues[2].trim(),
                    textSize = when (level) {
                        1 -> 18f
                        2 -> 16f
                        else -> 15f
                    },
                    bold = true,
                    topMargin = if (hasRendered) 12 else 0,
                    bottomMargin = 6
                )
                hasRendered = true
                continue
            }

            val listItem = LIST_ITEM_PATTERN.matchEntire(trimmed)
            if (listItem != null) {
                flushParagraph()
                addListItem(container, listItem.groupValues[1], listItem.groupValues[2])
                hasRendered = true
                continue
            }

            paragraph.add(trimmed)
        }

        flushParagraph()
    }

    private fun addTextView(
        container: LinearLayout,
        text: String,
        textSize: Float = 14f,
        bold: Boolean = false,
        topMargin: Int = 0,
        bottomMargin: Int = 4
    ) {
        val formatted = runCatching {
            MarkdownUtils.toSpannable(text, context)
        }.getOrDefault(android.text.SpannableString(text))
        if (bold && formatted is SpannableStringBuilder) {
            formatted.setSpan(StyleSpan(Typeface.BOLD), 0, formatted.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val tv = TextView(context).apply {
            this.text = formatted
            this.textSize = textSize
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setLineSpacing(0f, 1.5f)
            movementMethod = LinkMovementMethod.getInstance()
            gravity = android.view.Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.topMargin = topMargin
                this.bottomMargin = bottomMargin
            }
        }
        container.addView(tv)
    }

    private fun addListItem(container: LinearLayout, marker: String, body: String) {
        val prefix = if (marker == "-" || marker == "*") "\u2022" else marker
        val formattedBody = runCatching {
            MarkdownUtils.toSpannable(body.trim(), context)
        }.getOrDefault(android.text.SpannableString(body.trim()))
        val listText = SpannableStringBuilder().apply {
            append(prefix)
            append(" ")
            append(formattedBody)
            setSpan(LeadingMarginSpan.Standard(0, 34), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val tv = TextView(context).apply {
            text = listText
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setLineSpacing(0f, 1.5f)
            movementMethod = LinkMovementMethod.getInstance()
            gravity = android.view.Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2
                bottomMargin = 6
            }
        }
        container.addView(tv)
    }

    override fun getItemCount(): Int = messages.size

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastAiMessage(content: String) {
        if (messages.isNotEmpty() && messages.last().role == "assistant") {
            messages.last().content = content
            notifyItemChanged(messages.size - 1)
        }
    }
}
