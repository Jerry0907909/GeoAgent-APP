package com.geoagent.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.geoagent.R
import com.geoagent.domain.model.Message
import io.noties.markwon.Markwon

class ChatMessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Message>()
    private var markwon: Markwon? = null
    private var lastSize = 0

    fun submit(messages: List<Message>, markwon: Markwon) {
        this.markwon = markwon
        val isNewMessage = messages.size > lastSize
        lastSize = messages.size
        items.clear()
        items.addAll(messages)
        if (isNewMessage) {
            notifyItemInserted(items.size - 1)
        } else {
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].role == Message.ROLE_USER) TYPE_USER else TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(inflater.inflate(R.layout.item_message_user, parent, false))
        } else {
            AssistantHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Slide-up animation for the newest message
        if (position == items.size - 1 && lastSize > 0) {
            holder.itemView.translationY = 24f
            holder.itemView.alpha = 0.5f
            holder.itemView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }

        val message = items[position]
        if (holder is UserHolder) {
            holder.content.text = message.content
            if (!message.imageBase64.isNullOrBlank()) {
                holder.image.visibility = View.VISIBLE
                runCatching {
                    val bytes = Base64.decode(message.imageBase64, Base64.DEFAULT)
                    holder.image.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                }
            } else {
                holder.image.visibility = View.GONE
            }
        } else if (holder is AssistantHolder) {
            markwon?.setMarkdown(holder.content, message.content) ?: run {
                holder.content.text = message.content
            }
            holder.sourcesLayout.removeAllViews()
            if (message.sources.isNotEmpty()) {
                holder.sourcesLayout.visibility = View.VISIBLE
                message.sources.forEach { source ->
                    val tv = TextView(holder.itemView.context).apply {
                        text = "${source.source} · ${formatRelevance(source.relevance_score)}"
                        textSize = 12f
                        setTextColor(holder.itemView.context.getColor(R.color.text_muted))
                    }
                    holder.sourcesLayout.addView(tv)
                }
            } else {
                holder.sourcesLayout.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = items.size

    private fun formatRelevance(score: Float?): String {
        if (score == null) return "—"
        val pct = if (score <= 1.0) score * 100 else score
        return when {
            pct <= 0.0 -> "0%"
            pct < 1.0 -> "<1%"
            else -> "${pct.toInt()}%"
        }
    }

    private class UserHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.tv_content)
        val image: ImageView = view.findViewById(R.id.iv_image)
    }

    private class AssistantHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.tv_content)
        val sourcesLayout: LinearLayout = view.findViewById(R.id.layout_sources)
    }

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_ASSISTANT = 2
    }
}
