package com.geoagent.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.geoagent.R
import com.geoagent.domain.model.Message
import com.geoagent.model.SearchSource
import com.geoagent.ui.search.SearchSourceCard
import com.geoagent.ui.motion.MotionTokens
import com.geoagent.ui.motion.MotionUtils
import io.noties.markwon.Markwon

class ChatMessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Message>()
    private var markwon: Markwon? = null
    private var loadingText: String? = null
    private var lastAnimatedTimestamp: Long? = null

    fun submit(messages: List<Message>, markwon: Markwon, loadingText: String? = null) {
        this.markwon = markwon
        val oldMessages = items.toList()
        val oldLoadingText = this.loadingText

        if (oldMessages == messages && oldLoadingText == loadingText) return

        val streamingLastMessageOnly = oldLoadingText == loadingText &&
            oldMessages.size == messages.size &&
            oldMessages.dropLast(1) == messages.dropLast(1) &&
            oldMessages.lastOrNull() != messages.lastOrNull()

        if (streamingLastMessageOnly && messages.isNotEmpty()) {
            items.clear()
            items.addAll(messages)
            this.loadingText = loadingText
            notifyItemChanged(messages.lastIndex, PAYLOAD_CONTENT)
        } else {
            val diff = DiffUtil.calculateDiff(MessageDiff(oldMessages, messages, oldLoadingText, loadingText))
            items.clear()
            items.addAll(messages)
            this.loadingText = loadingText
            diff.dispatchUpdatesTo(this)
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (position >= items.size) return TYPE_LOADING
        return if (items[position].role == Message.ROLE_USER) TYPE_USER else TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserHolder(inflater.inflate(R.layout.item_message_user, parent, false))
            TYPE_LOADING -> LoadingHolder(inflater.inflate(R.layout.item_message_search_loading, parent, false))
            else -> AssistantHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is LoadingHolder) {
            holder.loading.text = loadingText ?: "智能搜索中…"
            holder.waveLoading.startAnimation()
            return
        }

        val message = items[position]
        animateIfNewMessage(holder.itemView, message, position)
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
            if (message.sources.isNotEmpty()) {
                holder.sourcesView.visibility = View.VISIBLE
                holder.sourcesView.setContent {
                    SearchSourceCard(
                        sources = message.sources.map {
                            SearchSource(
                                title = it.source,
                                url = it.url.orEmpty()
                            )
                        }
                    )
                }
            } else {
                holder.sourcesView.visibility = View.GONE
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_CONTENT) && position < items.size) {
            bindContentUpdate(holder, items[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = items.size + if (loadingText != null) 1 else 0

    private fun bindContentUpdate(holder: RecyclerView.ViewHolder, message: Message) {
        holder.itemView.animate().cancel()
        holder.itemView.translationY = 0f
        holder.itemView.alpha = 1f
        if (holder is AssistantHolder) {
            markwon?.setMarkdown(holder.content, message.content) ?: run {
                holder.content.text = message.content
            }
            if (message.sources.isNotEmpty()) {
                holder.sourcesView.visibility = View.VISIBLE
                holder.sourcesView.setContent {
                    SearchSourceCard(
                        sources = message.sources.map {
                            SearchSource(title = it.source, url = it.url.orEmpty())
                        }
                    )
                }
            } else {
                holder.sourcesView.visibility = View.GONE
            }
        } else if (holder is UserHolder) {
            holder.content.text = message.content
        } else if (holder is LoadingHolder) {
            holder.loading.text = loadingText ?: "智能搜索中…"
            holder.waveLoading.startAnimation()
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is LoadingHolder) holder.waveLoading.startAnimation()
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is LoadingHolder) holder.waveLoading.stopAnimation()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is LoadingHolder) holder.waveLoading.stopAnimation()
        super.onViewRecycled(holder)
    }

    private fun animateIfNewMessage(view: View, message: Message, position: Int) {
        if (position != items.lastIndex || lastAnimatedTimestamp == message.timestamp) {
            view.animate().cancel()
            view.translationY = 0f
            view.alpha = 1f
            return
        }
        lastAnimatedTimestamp = message.timestamp
        if (!MotionUtils.animationsEnabled()) return
        view.translationY = view.resources.displayMetrics.density * MotionTokens.CONTENT_OFFSET_DP
        view.alpha = 0.85f
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(MotionTokens.MICRO_MILLIS)
            .setInterpolator(MotionUtils.easeOut)
            .start()
    }

    private class MessageDiff(
        private val oldMessages: List<Message>,
        private val newMessages: List<Message>,
        private val oldLoadingText: String?,
        private val newLoadingText: String?
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldMessages.size + if (oldLoadingText != null) 1 else 0
        override fun getNewListSize(): Int = newMessages.size + if (newLoadingText != null) 1 else 0

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldLoading = oldItemPosition >= oldMessages.size
            val newLoading = newItemPosition >= newMessages.size
            if (oldLoading || newLoading) return oldLoading && newLoading
            return oldMessages[oldItemPosition].timestamp == newMessages[newItemPosition].timestamp &&
                oldMessages[oldItemPosition].role == newMessages[newItemPosition].role
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldLoading = oldItemPosition >= oldMessages.size
            val newLoading = newItemPosition >= newMessages.size
            if (oldLoading || newLoading) return oldLoadingText == newLoadingText
            return oldMessages[oldItemPosition] == newMessages[newItemPosition]
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val oldLoading = oldItemPosition >= oldMessages.size
            val newLoading = newItemPosition >= newMessages.size
            return if (oldLoading || newLoading) PAYLOAD_CONTENT else null
        }
    }

    private class UserHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.tv_content)
        val image: ImageView = view.findViewById(R.id.iv_image)
    }

    private class AssistantHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.tv_content)
        val sourcesView: ComposeView = view.findViewById(R.id.layout_sources)
    }

    private class LoadingHolder(view: View) : RecyclerView.ViewHolder(view) {
        val loading: TextView = view.findViewById(R.id.tv_loading)
        val waveLoading: WaveBarsLoadingView = view.findViewById(R.id.wave_loading)
    }

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_ASSISTANT = 2
        private const val TYPE_LOADING = 3
        private const val PAYLOAD_CONTENT = "content"
    }

}
