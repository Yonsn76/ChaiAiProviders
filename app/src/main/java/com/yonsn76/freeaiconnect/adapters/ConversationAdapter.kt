package com.yonsn76.freeaiconnect.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yonsn76.freeaiconnect.R
import com.yonsn76.freeaiconnect.models.Conversation

class ConversationAdapter(
    private val context: Context,
    private val onConversationClick: (Conversation) -> Unit,
    private val onDeleteClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    private val conversations = mutableListOf<Conversation>()
    var activeConversationId: String? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_conv_title)
        val tvPreview: TextView = view.findViewById(R.id.tv_conv_preview)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_conv_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conv = conversations[position]
        holder.tvTitle.text = conv.title
        holder.tvPreview.text = conv.messages.lastOrNull()?.content?.take(50) ?: "No messages yet"

        // Highlight active conversation
        val isActive = conv.id == activeConversationId
        holder.itemView.setBackgroundColor(
            if (isActive) context.getColor(R.color.canvas)
            else context.getColor(R.color.surface)
        )

        holder.itemView.setOnClickListener { onConversationClick(conv) }
        holder.btnDelete.setOnClickListener { onDeleteClick(conv) }
    }

    override fun getItemCount(): Int = conversations.size

    fun setConversations(newConversations: List<Conversation>) {
        conversations.clear()
        conversations.addAll(newConversations)
        notifyDataSetChanged()
    }
}
