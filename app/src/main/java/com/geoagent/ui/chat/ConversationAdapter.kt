package com.geoagent.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.geoagent.R
import com.geoagent.domain.model.Conversation

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.Holder>() {

    private val items = mutableListOf<Conversation>()

    fun submit(conversations: List<Conversation>) {
        items.clear()
        items.addAll(conversations)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.title?.ifBlank { "新对话" } ?: "新对话"
        holder.preview.text = item.lastMessage
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_title)
        val preview: TextView = view.findViewById(R.id.tv_preview)
    }
}
