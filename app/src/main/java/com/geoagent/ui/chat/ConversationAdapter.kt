package com.geoagent.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.geoagent.R
import com.geoagent.domain.model.Conversation
import com.geoagent.ui.motion.MotionUtils

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit,
    private val onRename: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.Holder>() {

    private val items = mutableListOf<Conversation>()

    fun submit(conversations: List<Conversation>) {
        val oldItems = items.toList()
        items.clear()
        items.addAll(conversations)
        DiffUtil.calculateDiff(ConversationDiff(oldItems, conversations)).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.title?.ifBlank { "新对话" } ?: "新对话"
        holder.preview.text = item.lastMessage
        holder.itemView.setOnClickListener {
            MotionUtils.press(holder.itemView)
            onClick(item)
        }
        holder.itemView.setOnLongClickListener {
            MotionUtils.press(holder.itemView)
            onRename(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_title)
        val preview: TextView = view.findViewById(R.id.tv_preview)
    }

    private class ConversationDiff(
        private val oldItems: List<Conversation>,
        private val newItems: List<Conversation>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldItems.size
        override fun getNewListSize(): Int = newItems.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition].id == newItems[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition] == newItems[newItemPosition]
        }
    }
}
