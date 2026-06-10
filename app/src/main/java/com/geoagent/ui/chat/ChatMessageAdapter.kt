package com.geoagent.ui.chat

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.MovementMethod
import android.text.style.ReplacementSpan
import android.text.style.ClickableSpan
import android.util.Base64
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.geoagent.R
import com.geoagent.data.api.dto.SourceDto
import com.geoagent.domain.model.Message
import com.geoagent.model.SearchSource
import com.geoagent.ui.search.SearchSourceCard
import com.geoagent.ui.search.SearchSourceSheet
import com.geoagent.ui.motion.MotionTokens
import com.geoagent.ui.motion.MotionUtils
import io.noties.markwon.Markwon

class ChatMessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Message>()
    private var markwon: Markwon? = null
    private var loadingText: String? = null
    private var isStreamingResponse = false
    private var lastAnimatedTimestamp: Long? = null
    private val expandedThinkingMessages = mutableSetOf<Long>()
    private val autoExpandedThinkingMessages = mutableSetOf<Long>()
    private var sourceSheetRequest: SourceSheetRequest? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            val activeIndex = items.indexOfLast { it.isThinkingActive() }
            if (activeIndex >= 0) {
                notifyItemChanged(activeIndex, PAYLOAD_CONTENT)
                timerHandler.postDelayed(this, THINKING_TIMER_INTERVAL_MILLIS)
            }
        }
    }

    fun submit(
        messages: List<Message>,
        markwon: Markwon,
        loadingText: String? = null,
        isStreamingResponse: Boolean = false
    ) {
        this.markwon = markwon
        val oldMessages = items.toList()
        val oldLoadingText = this.loadingText
        val oldStreamingResponse = this.isStreamingResponse

        if (oldMessages == messages && oldLoadingText == loadingText && oldStreamingResponse == isStreamingResponse) return

        val streamingLastMessageOnly = oldLoadingText == loadingText &&
            oldStreamingResponse == isStreamingResponse &&
            oldMessages.size == messages.size &&
            oldMessages.dropLast(1) == messages.dropLast(1) &&
            oldMessages.lastOrNull() != messages.lastOrNull()

        val onlyStreamingStateChanged = oldMessages == messages &&
            oldLoadingText == loadingText &&
            oldStreamingResponse != isStreamingResponse

        this.loadingText = loadingText
        this.isStreamingResponse = isStreamingResponse

        if (streamingLastMessageOnly && messages.isNotEmpty()) {
            items.clear()
            items.addAll(messages)
            notifyItemChanged(messages.lastIndex, PAYLOAD_CONTENT)
        } else if (onlyStreamingStateChanged) {
            val activeIndex = items.indexOfLast { it.role == Message.ROLE_ASSISTANT }
            if (activeIndex >= 0) notifyItemChanged(activeIndex, PAYLOAD_CONTENT)
        } else {
            val diff = DiffUtil.calculateDiff(MessageDiff(oldMessages, messages, oldLoadingText, loadingText))
            items.clear()
            items.addAll(messages)
            diff.dispatchUpdatesTo(this)
        }
        updateThinkingTimer()
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
            bindAssistant(holder, message, isStreamingMessage(position, message))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_CONTENT) && position < items.size) {
            bindContentUpdate(holder, position, items[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = items.size + if (loadingText != null) 1 else 0

    private fun bindContentUpdate(holder: RecyclerView.ViewHolder, position: Int, message: Message) {
        holder.itemView.animate().cancel()
        holder.itemView.translationY = 0f
        holder.itemView.alpha = 1f
        if (holder is AssistantHolder) {
            bindAssistant(holder, message, isStreamingMessage(position, message))
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

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        timerHandler.removeCallbacks(timerTick)
        super.onDetachedFromRecyclerView(recyclerView)
    }

    private fun bindAssistant(holder: AssistantHolder, message: Message, streaming: Boolean) {
        if (message.isEmptyAssistantPlaceholder()) {
            holder.itemView.visibility = View.GONE
            holder.itemView.layoutParams = holder.itemView.layoutParams.apply {
                height = 0
            }
            return
        }
        holder.itemView.visibility = View.VISIBLE
        holder.itemView.layoutParams = holder.itemView.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        bindThinking(holder, message, streaming)
        val sources = message.sources.map {
            SearchSource(
                title = it.source,
                url = it.url.orEmpty(),
                content = it.content,
                publishedDate = it.published_date
            )
        }
        if (streaming && sources.isEmpty()) {
            holder.content.movementMethod = null
            holder.content.linksClickable = false
            setIncrementalText(holder.content, message.content)
            holder.sourcesView.visibility = View.GONE
            return
        }
        val content = ensureReferenceMarker(cleanAssistantContent(message.content), sources)
        if (sources.isNotEmpty()) {
            markwon?.setMarkdown(holder.content, content) ?: run {
                holder.content.text = content
            }
            holder.content.text = buildReferenceClickableText(holder.content.text, sources) { source ->
                sourceSheetRequest = SourceSheetRequest(message.timestamp, listOf(source))
                notifyAssistantChanged(holder)
            }
            holder.content.movementMethod = ReferenceClickMovementMethod
            holder.content.highlightColor = Color.TRANSPARENT
            holder.content.linksClickable = true
        } else {
            holder.content.movementMethod = null
            markwon?.setMarkdown(holder.content, content) ?: run {
                holder.content.text = content
            }
        }
        if (sources.isNotEmpty()) {
            holder.sourcesView.visibility = View.VISIBLE
            holder.sourcesView.setContent {
                val request = sourceSheetRequest
                SearchSourceCard(sources = sources)
                SearchSourceSheet(
                    sources = request?.sources.orEmpty(),
                    visible = request?.messageTimestamp == message.timestamp,
                    onDismiss = {
                        if (sourceSheetRequest?.messageTimestamp == message.timestamp) {
                            sourceSheetRequest = null
                            notifyAssistantChanged(holder)
                        }
                    }
                )
            }
        } else {
            holder.sourcesView.visibility = View.GONE
        }
    }

    private fun isStreamingMessage(position: Int, message: Message): Boolean =
        isStreamingResponse &&
            position == items.indexOfLast { it.role == Message.ROLE_ASSISTANT } &&
            message.role == Message.ROLE_ASSISTANT &&
            message.sources.isEmpty()

    private fun setIncrementalText(view: TextView, value: String) {
        val current = view.text?.toString().orEmpty()
        if (current.isNotEmpty() && value.startsWith(current)) {
            val suffix = value.substring(current.length)
            if (suffix.isNotEmpty()) view.append(suffix)
        } else if (current != value) {
            view.text = value
        }
    }

    private fun ensureReferenceMarker(content: String, sources: List<SearchSource>): String {
        if (content.isBlank() || sources.isEmpty()) return content
        val hasReference = Regex("""(?:\[(\d+)]|【(\d+)】|［(\d+)］)""")
            .findAll(content)
            .mapNotNull { match -> match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.toIntOrNull() }
            .any { it in 1..sources.size }
        return if (hasReference) content else "$content [1]"
    }

    private fun notifyAssistantChanged(holder: AssistantHolder) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            notifyItemChanged(position, PAYLOAD_CONTENT)
        } else {
            notifyDataSetChanged()
        }
    }

    private fun buildReferenceClickableText(
        content: CharSequence,
        sources: List<SearchSource>,
        onSourceClick: (SearchSource) -> Unit
    ): SpannableString {
        val text = content.toString()
        val spannable = SpannableString(content)
        Regex("""\[(\d+)]|【(\d+)】|［(\d+)］""").findAll(text).forEach { match ->
            val number = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            val sourceIndex = number?.toIntOrNull()?.minus(1) ?: return@forEach
            if (sourceIndex !in sources.indices) return@forEach
            spannable.setSpan(
                ReferenceBadgeSpan(),
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        onSourceClick(sources[sourceIndex])
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = false
                    }
                },
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    private class ReferenceBadgeSpan : ReplacementSpan() {
        private val badgeSizePx = 22f
        private val gapPx = 5f
        private val backgroundColor = Color.rgb(244, 245, 247)
        private val iconColor = Color.rgb(137, 143, 153)
        private val iconText = "↗"

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return (badgeSizePx + gapPx).toInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val oldColor = paint.color
            val oldStyle = paint.style
            val oldTextSize = paint.textSize
            val fontMetrics = paint.fontMetrics
            val centerY = y + (fontMetrics.ascent + fontMetrics.descent) / 2f
            val rect = RectF(
                x,
                centerY - badgeSizePx / 2f,
                x + badgeSizePx,
                centerY + badgeSizePx / 2f
            )

            paint.style = Paint.Style.FILL
            paint.color = backgroundColor
            canvas.drawOval(rect, paint)

            paint.color = iconColor
            paint.textSize = oldTextSize * 0.72f
            paint.textAlign = Paint.Align.CENTER
            val iconY = centerY - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(iconText, rect.centerX(), iconY, paint)

            paint.color = oldColor
            paint.style = oldStyle
            paint.textSize = oldTextSize
            paint.textAlign = Paint.Align.LEFT
        }
    }

    private object ReferenceClickMovementMethod : MovementMethod {
        override fun initialize(widget: TextView, text: android.text.Spannable) = Unit

        override fun onTakeFocus(widget: TextView, text: android.text.Spannable, dir: Int) = Unit

        override fun canSelectArbitrarily(): Boolean = false

        override fun onKeyDown(
            widget: TextView,
            text: android.text.Spannable,
            keyCode: Int,
            event: android.view.KeyEvent
        ): Boolean = false

        override fun onKeyUp(
            widget: TextView,
            text: android.text.Spannable,
            keyCode: Int,
            event: android.view.KeyEvent
        ): Boolean = false

        override fun onKeyOther(
            view: TextView,
            text: android.text.Spannable,
            event: android.view.KeyEvent
        ): Boolean = false

        override fun onTrackballEvent(
            widget: TextView,
            text: android.text.Spannable,
            event: MotionEvent
        ): Boolean = false

        override fun onGenericMotionEvent(
            widget: TextView,
            text: android.text.Spannable,
            event: MotionEvent
        ): Boolean = false

        override fun onTouchEvent(widget: TextView, text: android.text.Spannable, event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val link = findLink(widget, text, event)
                widget.parent?.requestDisallowInterceptTouchEvent(link != null)
                return link != null
            }
            if (event.action != MotionEvent.ACTION_UP) {
                widget.parent?.requestDisallowInterceptTouchEvent(false)
                return false
            }
            val link = findLink(widget, text, event) ?: return false
            widget.parent?.requestDisallowInterceptTouchEvent(false)
            link.onClick(widget)
            return true
        }

        private fun findLink(
            widget: TextView,
            text: android.text.Spannable,
            event: MotionEvent
        ): ClickableSpan? {
            if (text.isEmpty()) return null
            val x = (event.x - widget.totalPaddingLeft + widget.scrollX).toInt()
            val y = (event.y - widget.totalPaddingTop + widget.scrollY).toInt()
            val layout = widget.layout ?: return null
            val line = layout.getLineForVertical(y)
            if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null
            val offset = layout.getOffsetForHorizontal(line, x.toFloat())
            val safeStart = offset.coerceIn(0, text.length - 1)
            val links = text.getSpans(safeStart, safeStart + 1, ClickableSpan::class.java)
            return links.firstOrNull()
        }
    }

    private fun bindThinking(holder: AssistantHolder, message: Message, streaming: Boolean) {
        val thinking = buildThinkingDisplay(message, streaming)
        val waitingForThinking = message.thinkingStartedAt != null && message.thinkingFinishedAt == null
        if (thinking.isEmpty() && !waitingForThinking) {
            holder.thinkingLayout.visibility = View.GONE
            holder.thinkingHeader.setOnClickListener(null)
            return
        }

        holder.thinkingLayout.visibility = View.VISIBLE
        if (autoExpandedThinkingMessages.add(message.timestamp)) {
            expandedThinkingMessages.add(message.timestamp)
        }
        val expanded = expandedThinkingMessages.contains(message.timestamp)
        holder.thinkingTitle.text = thinkingTitle(message)
        holder.thinkingBody.visibility = if (expanded) View.VISIBLE else View.GONE
        holder.thinkingToggle.rotation = if (expanded) 180f else 0f
        setIncrementalText(holder.thinkingContent, thinking.ifEmpty { "正在整理思路…" })
        holder.thinkingHeader.setOnClickListener {
            MotionUtils.press(it)
            val shouldCollapse = expandedThinkingMessages.contains(message.timestamp)
            if (shouldCollapse) {
                expandedThinkingMessages.remove(message.timestamp)
            } else {
                expandedThinkingMessages.add(message.timestamp)
            }
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                notifyItemChanged(position, PAYLOAD_CONTENT)
            }
        }
    }

    private fun buildThinkingDisplay(message: Message, streaming: Boolean): String {
        val thinking = if (streaming && message.thinkingFinishedAt == null) {
            formatStreamingThinkingForDisplay(message.thinkingContent)
        } else {
            formatThinkingForDisplay(message.thinkingContent)
        }
        if (message.sources.isEmpty() || message.thinkingStartedAt == null) return thinking
        val searchSummary = buildString {
            appendLine("已获取 ${message.sources.size} 条网页线索，正在筛选和整合信息。")
            append("接下来会合并重复内容，并在正文中保留来源编号。")
        }
        return when {
            thinking.isBlank() -> searchSummary
            thinking.contains("网页线索") || thinking.contains("联网检索") -> thinking
            else -> "$thinking\n$searchSummary"
        }
    }

    private fun cleanAssistantContent(content: String): String {
        val withoutSources = content
            .replace(Regex("""(?ims)^\s*(?:Sources|来源)\s*[:：]\s*[\s\S]*$"""), "")
            .replace(Regex("""(?m)^\s*[-•]\s+.*https?://\S+\s*$"""), "")
        return withoutSources.trim()
    }

    private fun Message.isEmptyAssistantPlaceholder(): Boolean =
        role == Message.ROLE_ASSISTANT &&
            content.isBlank() &&
            thinkingContent.isBlank() &&
            sources.isEmpty()

    private fun SourceDto.displayDomain(): String =
        url.orEmpty()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .ifBlank { source }

    private fun SourceDto.displayDateSuffix(): String {
        val date = published_date?.normalizeDate()
            ?: Regex("""20\d{2}[-/.年]\d{1,2}(?:[-/.月]\d{1,2})?""")
                .find("$source $content ${url.orEmpty()}")
                ?.value
                ?.normalizeDate()
        return date?.let { "，$it" }.orEmpty()
    }

    private fun String.normalizeDate(): String? {
        val cleaned = trim()
            .replace("年", "/")
            .replace("月", "/")
            .replace("日", "")
            .replace("-", "/")
            .replace(".", "/")
        val parts = cleaned.split("/")
            .filter { it.isNotBlank() }
            .take(3)
        if (parts.size < 2) return null
        val year = parts[0]
        val month = parts[1].padStart(2, '0')
        val day = parts.getOrNull(2)?.padStart(2, '0')
        return if (day == null) "$year/$month" else "$year/$month/$day"
    }

    private fun thinkingTitle(message: Message): String {
        val startedAt = message.thinkingStartedAt ?: return "已思考"
        val finishedAt = message.thinkingFinishedAt
        val elapsedSeconds = ((finishedAt ?: System.currentTimeMillis()) - startedAt)
            .coerceAtLeast(0L) / 1000L
        return if (finishedAt == null) {
            "思考中 ${elapsedSeconds} 秒"
        } else {
            "已思考 ${elapsedSeconds.coerceAtLeast(1L)} 秒"
        }
    }

    private fun updateThinkingTimer() {
        timerHandler.removeCallbacks(timerTick)
        if (items.any { it.isThinkingActive() }) {
            timerHandler.postDelayed(timerTick, THINKING_TIMER_INTERVAL_MILLIS)
        }
    }

    private fun Message.isThinkingActive(): Boolean {
        return role == Message.ROLE_ASSISTANT &&
            thinkingStartedAt != null &&
            thinkingFinishedAt == null
    }

    private fun formatThinkingForDisplay(raw: String): String {
        return raw.withoutAnswerDraft()
            .withoutInternalInstructionBlock()
            .replace(Regex("""\s*[（(][A-Za-z][^（）()]*[）)]"""), "")
            .replace(Regex("""(?i)\b(?:thinking\s*process|user\s*input|my\s*role|goal|acknowledge|reiterate|offer\s+assistance|maintain|refine\s+the\s+response|list\s+capabilities|option\s*\d*)\s*[:：]?\s*\d*\.?"""), "")
            .replace(Regex("""(?i)\*\*[^*\u4e00-\u9fff]*(?:process|input|role|goal|option|response|capabilities)[^*]*\*\*"""), "")
            .replace(Regex("""\*\*"""), "")
            .replace(Regex("""(?m)^\s*[*\-•]?\s*[A-Za-z][A-Za-z0-9\s'",.&:/?+\-]*:?\s*$"""), "")
            .replace(Regex("""(?m)^\s*[*\-•]\s*(?=[A-Za-z])[^。！？\n\u4e00-\u9fff]*$"""), "")
            .replace(Regex("""[A-Za-z][A-Za-z0-9\s'",.&:/?+\-]{18,}(?=[。！？，、；：\n]|$)"""), "")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.any { char -> char in '\u4e00'..'\u9fff' } }
            .filterNot { it.isLowInformationThinkingLine() }
            .dropUnfinishedTailLine()
            .joinToString("\n")
            .trim()
    }

    private fun formatStreamingThinkingForDisplay(raw: String): String {
        return raw.withoutAnswerDraft()
            .withoutInternalInstructionBlock()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.any { char -> char in '\u4e00'..'\u9fff' } }
            .filterNot { it.isLowInformationThinkingLine() }
            .dropUnfinishedTailLine()
            .joinToString("\n")
            .trim()
    }

    private fun String.withoutAnswerDraft(): String {
        return replace(
            Regex("""(?ims)(?:^|\n)\s*(?:草稿|回答草稿|最终回答|正式回答)\s*[:：][\s\S]*$"""),
            ""
        )
    }

    private fun String.withoutInternalInstructionBlock(): String {
        return replace(
            Regex("""(?ims)(?:^|\n)\s*(?:要求|问信息|用户要求|回答要求|输出要求|要点|提炼要点)\s*[:：][\s\S]*$"""),
            ""
        )
    }

    private fun List<String>.dropUnfinishedTailLine(): List<String> {
        val last = lastOrNull() ?: return this
        val normalized = last.trim()
        if (normalized.isEmpty()) return this
        val looksLikeListPrefix = Regex("""^\d+[.、]\s*\S+""").containsMatchIn(normalized)
        val endsCleanly = normalized.last() in setOf('。', '！', '？', '.', '!', '?', '」', '”', '）', ')')
        return if (looksLikeListPrefix && !endsCleanly) dropLast(1) else this
    }

    private fun String.isLowInformationThinkingLine(): Boolean {
        val normalized = replace(Regex("""[，。！？、；：,.!?;:\s]"""), "")
        return normalized.length <= 3 && normalized in setOf(
            "好",
            "好的",
            "嗯",
            "我们",
            "可以",
            "明白",
            "行"
        )
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
        val thinkingLayout: LinearLayout = view.findViewById(R.id.layout_thinking)
        val thinkingHeader: LinearLayout = view.findViewById(R.id.row_thinking_header)
        val thinkingTitle: TextView = view.findViewById(R.id.tv_thinking_title)
        val thinkingBody: LinearLayout = view.findViewById(R.id.layout_thinking_body)
        val thinkingContent: TextView = view.findViewById(R.id.tv_thinking_content)
        val thinkingToggle: ImageView = view.findViewById(R.id.iv_thinking_toggle)
        val content: TextView = view.findViewById(R.id.tv_content)
        val sourcesView: ComposeView = view.findViewById(R.id.layout_sources)
    }

    private class LoadingHolder(view: View) : RecyclerView.ViewHolder(view) {
        val loading: TextView = view.findViewById(R.id.tv_loading)
        val waveLoading: WaveBarsLoadingView = view.findViewById(R.id.wave_loading)
    }

    private data class SourceSheetRequest(
        val messageTimestamp: Long,
        val sources: List<SearchSource>
    )

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_ASSISTANT = 2
        private const val TYPE_LOADING = 3
        private const val PAYLOAD_CONTENT = "content"
        private const val THINKING_TIMER_INTERVAL_MILLIS = 1_000L
    }

}
