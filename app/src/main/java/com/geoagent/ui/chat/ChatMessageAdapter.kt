package com.geoagent.ui.chat

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.content.Intent
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
import com.geoagent.model.isKnowledgeBaseSource
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.documents.DocumentDetailActivity
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

    private var lastStreamingThinkingHash: Int = 0

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
            oldMessages.lastOrNull() != messages.lastOrNull() &&
            oldMessages.lastOrNull()?.sources == messages.lastOrNull()?.sources

        val onlyStreamingStateChanged = oldMessages == messages &&
            oldLoadingText == loadingText &&
            oldStreamingResponse != isStreamingResponse

        this.loadingText = loadingText
        this.isStreamingResponse = isStreamingResponse

        if (streamingLastMessageOnly && messages.isNotEmpty()) {
            val newMsg = messages.last()
            val newThinkingHash = newMsg.thinkingContent.hashCode() + newMsg.thinkingStartedAt.hashCode() + newMsg.thinkingFinishedAt.hashCode()
            val thinkingUnchanged = newThinkingHash == lastStreamingThinkingHash
            lastStreamingThinkingHash = newThinkingHash
            items.clear()
            items.addAll(messages)
            notifyItemChanged(messages.lastIndex, if (thinkingUnchanged) PAYLOAD_CONTENT_ONLY else PAYLOAD_CONTENT)
        } else if (onlyStreamingStateChanged) {
            val activeIndex = items.indexOfLast { it.role == Message.ROLE_ASSISTANT }
            if (activeIndex >= 0) notifyItemChanged(activeIndex, PAYLOAD_CONTENT)
        } else {
            lastStreamingThinkingHash = 0
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
        if (payloads.contains(PAYLOAD_CONTENT_ONLY) && position < items.size && holder is AssistantHolder) {
            holder.itemView.animate().cancel()
            holder.itemView.translationY = 0f
            holder.itemView.alpha = 1f
            val message = items[position]
            if (!message.isEmptyAssistantPlaceholder()) {
                renderStreamingMarkdown(holder.content, message.content)
            }
        } else if (payloads.contains(PAYLOAD_CONTENT) && position < items.size) {
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
                publishedDate = it.published_date,
                type = it.type,
                documentId = it.document_id
            )
        }
        if (streaming && sources.isEmpty()) {
            holder.content.movementMethod = null
            holder.content.linksClickable = false
            renderStreamingMarkdown(holder.content, message.content)
            holder.sourcesView.visibility = View.GONE
            return
        }
        val content = ensureReferenceMarker(cleanAssistantContent(message.content), sources)
        if (sources.isNotEmpty()) {
            markwon?.setMarkdown(holder.content, content) ?: run {
                holder.content.text = content
            }
            holder.content.text = buildReferenceClickableText(holder.content.text, sources) { clickedSources ->
                sourceSheetRequest = SourceSheetRequest(message.timestamp, clickedSources)
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
                SearchSourceCard(
                    sources = sources,
                    onSourceClick = { source -> openSource(holder.itemView, source) }
                )
                SearchSourceSheet(
                    sources = request?.sources.orEmpty(),
                    visible = request?.messageTimestamp == message.timestamp,
                    onSourceClick = { source -> openSource(holder.itemView, source) },
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

    private fun openSource(anchor: View, source: SearchSource) {
        if (source.isKnowledgeBaseSource()) {
            openDocumentSource(anchor, source)
        }
    }

    private fun openDocumentSource(anchor: View, source: SearchSource) {
        val documentId = source.documentId?.takeIf { it.isNotBlank() } ?: return
        val context = anchor.context
        context.startActivity(
            Intent(context, DocumentDetailActivity::class.java)
                .putExtra(DocumentDetailActivity.EXTRA_DOCUMENT_ID, documentId)
                .putExtra(DocumentDetailActivity.EXTRA_SOURCE, source.title)
                .putExtra(DocumentDetailActivity.EXTRA_COLLECTION, "local")
        )
        (context as? Activity)?.let { TransitionHelper.forward(it) }
    }

    private fun isStreamingMessage(position: Int, message: Message): Boolean =
        isStreamingResponse &&
            position == items.indexOfLast { it.role == Message.ROLE_ASSISTANT } &&
            message.role == Message.ROLE_ASSISTANT &&
            message.sources.isEmpty()

    private var lastMarkwonRenderMs: Long = 0

    private fun renderStreamingMarkdown(view: TextView, content: String) {
        val now = System.currentTimeMillis()
        if (now - lastMarkwonRenderMs < MARKWON_RENDER_INTERVAL_MS) return
        lastMarkwonRenderMs = now
        val markwon = this.markwon
        if (markwon != null) {
            markwon.setMarkdown(view, content)
        } else {
            view.text = content
        }
    }

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
        val hasReference = referenceRegex().containsMatchIn(content)
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
        onSourceClick: (List<SearchSource>) -> Unit
    ): SpannableString {
        val text = content.toString()
        val spannable = SpannableString(content)
        referenceRegex().findAll(text).forEach { match ->
            val clickedSources = if (match.value.isSourceWordReference()) {
                sources
            } else {
                val number = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
                val sourceIndex = number?.toIntOrNull()?.minus(1) ?: return@forEach
                sources.getOrNull(sourceIndex)?.let { listOf(it) } ?: sources
            }
            spannable.setSpan(
                ReferenceBadgeSpan(),
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        onSourceClick(clickedSources)
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

    private fun referenceRegex(): Regex =
        Regex("""\[(\d+)]|【(\d+)】|［(\d+)］|【\s*来源\s*】|\[\s*来源\s*]""")

    private fun String.isSourceWordReference(): Boolean =
        contains("来源")

    private class ReferenceBadgeSpan : ReplacementSpan() {
        private val backgroundColor = Color.rgb(244, 245, 247)
        private val iconColor = Color.rgb(137, 143, 153)

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val badgeSizePx = badgeSizePx(paint)
            if (fm != null) {
                val original = paint.fontMetricsInt
                val center = (original.ascent + original.descent) / 2
                val half = (badgeSizePx / 2f + 0.5f).toInt()
                fm.ascent = minOf(original.ascent, center - half)
                fm.descent = maxOf(original.descent, center + half)
                fm.top = minOf(original.top, fm.ascent)
                fm.bottom = maxOf(original.bottom, fm.descent)
            }
            return (badgeSizePx + gapPx(paint) + 0.5f).toInt()
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
            val badgeSizePx = badgeSizePx(paint)
            val oldColor = paint.color
            val oldStyle = paint.style
            val oldTextSize = paint.textSize
            val oldStrokeWidth = paint.strokeWidth
            val oldStrokeCap = paint.strokeCap
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

            val strokeWidth = badgeSizePx * 0.066f
            val linkWidth = badgeSizePx * 0.33f
            val linkHeight = badgeSizePx * 0.20f
            val linkRadius = linkHeight / 2f
            val firstLink = RectF(
                rect.centerX() - badgeSizePx * 0.29f,
                rect.centerY() - linkHeight / 2f,
                rect.centerX() - badgeSizePx * 0.29f + linkWidth,
                rect.centerY() + linkHeight / 2f
            )
            val secondLink = RectF(
                rect.centerX() - badgeSizePx * 0.06f,
                rect.centerY() - linkHeight / 2f,
                rect.centerX() - badgeSizePx * 0.06f + linkWidth,
                rect.centerY() + linkHeight / 2f
            )

            paint.color = iconColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.strokeCap = Paint.Cap.ROUND
            canvas.save()
            canvas.rotate(-35f, rect.centerX(), rect.centerY())
            canvas.drawRoundRect(firstLink, linkRadius, linkRadius, paint)
            canvas.drawRoundRect(secondLink, linkRadius, linkRadius, paint)
            canvas.restore()

            paint.color = oldColor
            paint.style = oldStyle
            paint.textSize = oldTextSize
            paint.strokeWidth = oldStrokeWidth
            paint.strokeCap = oldStrokeCap
            paint.textAlign = Paint.Align.LEFT
        }

        private fun badgeSizePx(paint: Paint): Float {
            val density = (paint as? TextPaint)?.density?.takeIf { it > 0f } ?: 1f
            return maxOf(20f * density, paint.textSize * 0.95f)
        }

        private fun gapPx(paint: Paint): Float {
            val density = (paint as? TextPaint)?.density?.takeIf { it > 0f } ?: 1f
            return 4f * density
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
            return findClickableSpanNear(text, offset)
        }

        private fun findClickableSpanNear(text: android.text.Spannable, offset: Int): ClickableSpan? {
            if (text.isEmpty()) return null
            val candidates = intArrayOf(offset, offset - 1, offset + 1)
            for (candidate in candidates) {
                val safeStart = candidate.coerceIn(0, text.length - 1)
                val links = text.getSpans(safeStart, safeStart + 1, ClickableSpan::class.java)
                if (links.isNotEmpty()) return links.first()
            }
            return null
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
            .replace(RE_SOURCES_BLOCK, "")
            .replace(RE_SOURCE_LINKS, "")
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
            ?: RE_DATE_IN_TEXT
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
            .replace(RE_ENGLISH_PAREN, "")
            .replace(RE_ENGLISH_KEYWORDS, "")
            .replace(RE_ENGLISH_BOLD, "")
            .replace(RE_BOLD_MARKERS, "")
            .replace(RE_ENGLISH_LINE, "")
            .replace(RE_ENGLISH_BULLET_LINE, "")
            .replace(RE_LONG_ENGLISH, "")
            .replace(RE_MULTI_SPACE, " ")
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
            RE_ANSWER_DRAFT,
            ""
        )
    }

    private fun String.withoutInternalInstructionBlock(): String {
        return replace(
            RE_INTERNAL_INSTRUCTION,
            ""
        )
    }

    private fun List<String>.dropUnfinishedTailLine(): List<String> {
        val last = lastOrNull() ?: return this
        val normalized = last.trim()
        if (normalized.isEmpty()) return this
        val looksLikeListPrefix = RE_LIST_PREFIX.containsMatchIn(normalized)
        val endsCleanly = normalized.last() in setOf('。', '！', '？', '.', '!', '?', '」', '”', '）', ')')
        return if (looksLikeListPrefix && !endsCleanly) dropLast(1) else this
    }

    private fun String.isLowInformationThinkingLine(): Boolean {
        val normalized = replace(RE_PUNCTUATION_ONLY, "")
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
        private const val PAYLOAD_CONTENT_ONLY = "content_only"
        private const val THINKING_TIMER_INTERVAL_MILLIS = 1_000L
        private const val MARKWON_RENDER_INTERVAL_MS = 60L

        private val RE_ENGLISH_PAREN = Regex("""\s*[（(][A-Za-z][^（）()]*[）)]""")
        private val RE_ENGLISH_KEYWORDS = Regex("""(?i)\b(?:thinking\s*process|user\s*input|my\s*role|goal|acknowledge|reiterate|offer\s+assistance|maintain|refine\s+the\s+response|list\s+capabilities|option\s*\d*)\s*[:：]?\s*\d*\.?""")
        private val RE_ENGLISH_BOLD = Regex("""(?i)\*\*[^*\u4e00-\u9fff]*(?:process|input|role|goal|option|response|capabilities)[^*]*\*\*""")
        private val RE_BOLD_MARKERS = Regex("""\*\*""")
        private val RE_ENGLISH_LINE = Regex("""(?m)^\s*[*\-•]?\s*[A-Za-z][A-Za-z0-9\s'",.&:/?+\-]*:?\s*$""")
        private val RE_ENGLISH_BULLET_LINE = Regex("""(?m)^\s*[*\-•]\s*(?=[A-Za-z])[^。！？\n\u4e00-\u9fff]*$""")
        private val RE_LONG_ENGLISH = Regex("""[A-Za-z][A-Za-z0-9\s'",.&:/?+\-]{18,}(?=[。！？，、；：\n]|$)""")
        private val RE_MULTI_SPACE = Regex("""[ \t]{2,}""")
        private val RE_ANSWER_DRAFT = Regex("""(?ims)(?:^|\n)\s*(?:草稿|回答草稿|最终回答|正式回答)\s*[:：][\s\S]*$""")
        private val RE_INTERNAL_INSTRUCTION = Regex("""(?ims)(?:^|\n)\s*(?:要求|问信息|用户要求|回答要求|输出要求|要点|提炼要点)\s*[:：][\s\S]*$""")
        private val RE_SOURCES_BLOCK = Regex("""(?ims)^\s*(?:Sources|来源)\s*[:：]\s*[\s\S]*$""")
        private val RE_SOURCE_LINKS = Regex("""(?m)^\s*[-•]\s+.*https?://\S+\s*$""")
        private val RE_DATE_IN_TEXT = Regex("""20\d{2}[-/.年]\d{1,2}(?:[-/.月]\d{1,2})?""")
        private val RE_LIST_PREFIX = Regex("""^\d+[.、]\s*\S+""")
        private val RE_PUNCTUATION_ONLY = Regex("""[，。！？、；：,.!?;:\s]""")
    }

}
