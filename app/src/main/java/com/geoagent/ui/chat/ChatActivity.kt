package com.geoagent.ui.chat

import android.Manifest
import android.graphics.Color
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.CalendarContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geoagent.R
import com.geoagent.domain.model.ChatMode
import com.geoagent.domain.model.Message
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.documents.DocumentListActivity
import com.geoagent.ui.motion.MotionTokens
import com.geoagent.ui.motion.MotionUtils
import com.geoagent.ui.settings.SettingsActivity
import com.geoagent.ui.viewmodel.ChatViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    private val chatManager: ChatViewModel by viewModels()

    private lateinit var messageAdapter: ChatMessageAdapter
    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var markwon: Markwon
    private lateinit var modeSwitch: DeepSeekModeSwitch
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageView

    private var pendingImageBase64: String? = null
    private var syncingSliders = false
    private var lastDisplayedMode: ChatMode? = null
    private var lastHasMessages: Boolean? = null
    private var lastRagSettingsVisible = false
    private var lastModeChipIsRag: Boolean? = null
    private var shouldAutoScrollMessages = true
    private var userPausedAutoScroll = false
    private var messagesScrollState = RecyclerView.SCROLL_STATE_IDLE

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            this,
            if (granted) "提醒通知已开启" else "提醒已创建，但通知权限未开启",
            Toast.LENGTH_SHORT
        ).show()
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                pendingImageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Toast.makeText(this, "图片已选择", Toast.LENGTH_SHORT).show()
                updateSendButtonState()
            }
        }.onFailure {
            Toast.makeText(this, "图片读取失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        markwon = Markwon.create(this)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val rvMessages = findViewById<RecyclerView>(R.id.rv_messages)
        val rvConversations = findViewById<RecyclerView>(R.id.rv_conversations)
        val emptyLayout = findViewById<View>(R.id.layout_empty_chat)
        val emptyLogo = findViewById<ImageView>(R.id.iv_empty_logo)
        val tvEmptyTitle = findViewById<TextView>(R.id.tv_empty_title)
        val tvModeHint = findViewById<TextView>(R.id.tv_mode_hint)
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        val btnModeChat = findViewById<TextView>(R.id.btn_mode_chat)
        val btnModeRag = findViewById<TextView>(R.id.btn_mode_rag)
        val chipWebSearch = findViewById<TextView>(R.id.chip_web_search)
        val chipDeepThinking = findViewById<TextView?>(R.id.chip_deep_thinking)
        val chipRagSettings = findViewById<TextView>(R.id.chip_rag_settings)
        val btnAddImage = findViewById<ImageView>(R.id.btn_add_image)
        val cardRagSettings = findViewById<MaterialCardView>(R.id.card_rag_settings)
        val sliderTopK = findViewById<Slider>(R.id.slider_top_k)
        val sliderMinRelevance = findViewById<Slider>(R.id.slider_min_relevance)

        modeSwitch = DeepSeekModeSwitch(
            host = findViewById(R.id.mode_switch_container),
            indicator = findViewById(R.id.mode_switch_indicator),
            chatTab = btnModeChat,
            ragTab = btnModeRag
        )

        findViewById<ImageView>(R.id.btn_menu).setOnClickListener {
            MotionUtils.press(it)
            drawerLayout.openDrawer(GravityCompat.START)
            chatManager.refreshConversations()
        }
        findViewById<ImageView>(R.id.btn_new_chat).setOnClickListener {
            MotionUtils.press(it)
            chatManager.resetChat()
        }

        messageAdapter = ChatMessageAdapter()
        rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = false }
        rvMessages.itemAnimator = null
        rvMessages.adapter = messageAdapter
        rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                messagesScrollState = newState
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    userPausedAutoScroll = true
                    shouldAutoScrollMessages = false
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (recyclerView.isNearBottom()) {
                        userPausedAutoScroll = false
                        shouldAutoScrollMessages = true
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (recyclerView.isNearBottom()) {
                    if (userPausedAutoScroll) userPausedAutoScroll = false
                    shouldAutoScrollMessages = true
                }
            }
        })

        conversationAdapter = ConversationAdapter(
            onClick = { conversation ->
                chatManager.loadConversation(conversation.id)
                drawerLayout.closeDrawer(GravityCompat.START)
            },
            onRename = { conversation ->
                showRenameConversationDialog(conversation.id, conversation.title.orEmpty())
            }
        )
        rvConversations.layoutManager = LinearLayoutManager(this)
        MotionUtils.setupRecyclerItemAnimator(rvConversations)
        rvConversations.adapter = conversationAdapter

        val conversationId = intent.getIntExtra(EXTRA_CONVERSATION_ID, 0)
        if (conversationId > 0) chatManager.loadConversation(conversationId) else chatManager.resetChat()

        btnModeChat.setOnClickListener {
            if (chatManager.uiState.value.currentMode != ChatMode.CHAT) {
                chatManager.setMode(ChatMode.CHAT)
            }
        }
        btnModeRag.setOnClickListener {
            if (chatManager.uiState.value.currentMode != ChatMode.RAG) {
                chatManager.setMode(ChatMode.RAG)
            }
        }

        chipWebSearch.setOnClickListener {
            MotionUtils.press(it)
            chatManager.setWebSearchEnabled(!chatManager.uiState.value.webSearchEnabled)
        }
        chipDeepThinking?.setOnClickListener {
            MotionUtils.press(it)
            chatManager.setDeepThinkingEnabled(!chatManager.uiState.value.deepThinkingEnabled)
        }
        btnAddImage.setOnClickListener {
            MotionUtils.press(it)
            imagePicker.launch("image/*")
        }
        chipRagSettings.setOnClickListener {
            MotionUtils.press(it)
            val expanded = cardRagSettings.visibility != View.VISIBLE
            updateRagSettingsVisibility(cardRagSettings, expanded)
            chatManager.setRagSettingsExpanded(expanded)
        }

        sliderTopK.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !syncingSliders) chatManager.setRagTopK(value.toInt())
        }
        sliderMinRelevance.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !syncingSliders) chatManager.setRagMinRelevanceScore(value)
        }

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateSendButtonState()
        })

        btnSend.setOnClickListener {
            val text = etMessage.text?.toString()?.trim().orEmpty()
            if (text.isEmpty() && pendingImageBase64 == null) {
                MotionUtils.press(btnSend)
                return@setOnClickListener
            }
            MotionUtils.press(btnSend)
            userPausedAutoScroll = false
            shouldAutoScrollMessages = true
            chatManager.sendMessage(text.ifEmpty { "请分析这张图片" }, pendingImageBase64)
            etMessage.text?.clear()
            pendingImageBase64 = null
            updateSendButtonState()
        }

        findViewById<MaterialButton>(R.id.btn_drawer_documents).setOnClickListener {
            MotionUtils.press(it)
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, DocumentListActivity::class.java))
            TransitionHelper.forward(this)
        }
        findViewById<MaterialButton>(R.id.btn_drawer_settings).setOnClickListener {
            MotionUtils.press(it)
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
            TransitionHelper.forward(this)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                TransitionHelper.backward(this@ChatActivity)
            }
        })

        updateSendButtonState()

        lifecycleScope.launch {
            chatManager.uiState.collectLatest { state ->
                val loadingText = state.loadingDisplayText()
                messageAdapter.submit(state.messages, markwon, loadingText)
                conversationAdapter.submit(state.conversations)
                val hasMessages = state.messages.isNotEmpty()
                updateEmptyState(emptyLayout, emptyLogo, hasMessages)
                updateMessagesVisibility(rvMessages, hasMessages)
                if (hasMessages && shouldAutoScrollMessages && !userPausedAutoScroll) {
                    rvMessages.post {
                        if (shouldAutoScrollMessages && !userPausedAutoScroll && messageAdapter.itemCount > 0) {
                            followStreamingIfAllowed(rvMessages)
                        }
                    }
                }
                tvStatus.clearAnimation()
                tvStatus.visibility = View.GONE

                state.error?.let {
                    Toast.makeText(this@ChatActivity, it, Toast.LENGTH_SHORT).show()
                    chatManager.clearError()
                }
                state.retrievalHint?.let {
                    Toast.makeText(this@ChatActivity, it, Toast.LENGTH_LONG).show()
                    chatManager.clearRetrievalHint()
                }
                state.conversationError?.let {
                    Toast.makeText(this@ChatActivity, it, Toast.LENGTH_SHORT).show()
                    chatManager.clearConversationError()
                }

                when (state.pendingAgentNavigation) {
                    AgentNavigationTarget.DOCUMENTS -> {
                        startActivity(Intent(this@ChatActivity, DocumentListActivity::class.java))
                        TransitionHelper.forward(this@ChatActivity)
                        chatManager.consumePendingNavigation()
                    }
                    AgentNavigationTarget.SETTINGS -> {
                        startActivity(Intent(this@ChatActivity, SettingsActivity::class.java))
                        TransitionHelper.forward(this@ChatActivity)
                        chatManager.consumePendingNavigation()
                    }
                    null -> Unit
                }

                state.pendingSystemAction?.let { action ->
                    handleV2SystemAction(action)
                    chatManager.consumePendingSystemAction()
                }

                updateModeUi(state.currentMode, chipWebSearch, chipRagSettings, tvEmptyTitle, tvModeHint, hasMessages)
                updateSearchChip(chipWebSearch, state.webSearchEnabled)
                if (chipDeepThinking != null) updateDeepThinkingChip(chipDeepThinking!!, state.deepThinkingEnabled)
                updateRagSettingsVisibility(
                    cardRagSettings,
                    state.ragSettingsExpanded && state.currentMode == ChatMode.RAG
                )

                syncingSliders = true
                sliderTopK.value = state.ragTopK.toFloat()
                sliderMinRelevance.value = state.ragMinRelevanceScore
                syncingSliders = false
            }
        }
    }

    private fun updateEmptyState(emptyLayout: View, emptyLogo: ImageView, hasMessages: Boolean) {
        val previous = lastHasMessages
        if (previous == hasMessages) return
        lastHasMessages = hasMessages
        if (hasMessages) {
            emptyLogo.animate().cancel()
            emptyLogo.scaleX = 1f
            emptyLogo.scaleY = 1f
            emptyLogo.alpha = 1f
            MotionUtils.hide(emptyLayout, endVisibility = View.GONE)
        } else {
            MotionUtils.show(emptyLayout, MotionTokens.ENTER_MILLIS)
            startEmptyLogoPulse(emptyLogo)
        }
    }

    private fun startEmptyLogoPulse(logo: ImageView) {
        logo.animate().cancel()
        if (!MotionUtils.animationsEnabled()) return
        logo.animate()
            .scaleX(1.04f)
            .scaleY(1.04f)
            .alpha(0.9f)
            .setDuration(900L)
            .setInterpolator(MotionUtils.easeOutSoft)
            .withEndAction {
                logo.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(900L)
                    .setInterpolator(MotionUtils.easeOutSoft)
                    .withEndAction {
                        if (logo.isAttachedToWindow && logo.visibility == View.VISIBLE) startEmptyLogoPulse(logo)
                    }
                    .start()
            }
            .start()
    }

    private fun updateMessagesVisibility(rvMessages: RecyclerView, hasMessages: Boolean) {
        if (hasMessages) {
            if (rvMessages.visibility != View.VISIBLE) {
                MotionUtils.show(rvMessages, MotionTokens.ENTER_MILLIS)
            }
        } else if (rvMessages.visibility != View.INVISIBLE) {
            MotionUtils.hide(rvMessages, endVisibility = View.INVISIBLE)
        }
    }

    private fun followStreamingIfAllowed(rvMessages: RecyclerView) {
        if (messageAdapter.itemCount == 0) return
        if (messagesScrollState == RecyclerView.SCROLL_STATE_DRAGGING || userPausedAutoScroll) return
        val distance = rvMessages.distanceToBottom()
        if (distance <= 0) return
        rvMessages.scrollBy(0, distance)
    }

    private fun updateRagSettingsVisibility(card: View, visible: Boolean) {
        if (lastRagSettingsVisible == visible && card.visibility == if (visible) View.VISIBLE else View.GONE) return
        lastRagSettingsVisible = visible
        if (visible) {
            MotionUtils.show(card, MotionTokens.STATE_MILLIS)
        } else {
            MotionUtils.hide(card, MotionTokens.EXIT_MILLIS, View.GONE)
        }
    }

    private fun RecyclerView.isNearBottom(): Boolean {
        return distanceToBottom() <= NEAR_BOTTOM_THRESHOLD_PX
    }

    private fun RecyclerView.distanceToBottom(): Int =
        (computeVerticalScrollRange() - computeVerticalScrollOffset() - computeVerticalScrollExtent()).coerceAtLeast(0)

    private fun ChatUiState.loadingDisplayText(): String? {
        if (!isLoading) return null
        val activeThinking = messages.lastOrNull()?.let {
            it.role == Message.ROLE_ASSISTANT && it.thinkingStartedAt != null && it.thinkingFinishedAt == null
        } == true
        if (activeThinking) return null
        val answerStarted = messages.lastOrNull()?.let {
            it.role == Message.ROLE_ASSISTANT && it.content.isNotBlank()
        } == true
        val thinkingStarted = messages.lastOrNull()?.let {
            it.role == Message.ROLE_ASSISTANT && it.thinkingContent.isNotBlank()
        } == true
        if (thinkingStarted) return null
        if (answerStarted) return null
        if (!statusMessage.isNullOrBlank()) return statusMessage
        return when {
            currentMode == ChatMode.CHAT && webSearchEnabled -> "智能搜索中…"
            currentMode == ChatMode.RAG -> statusMessage ?: "正在检索知识库…"
            else -> "正在生成回答…"
        }
    }

    private fun updateSendButtonState() {
        val hasContent = etMessage.text?.toString()?.trim()?.isNotEmpty() == true || pendingImageBase64 != null
        btnSend.isEnabled = hasContent
        btnSend.setImageResource(R.drawable.ic_send_arrow)
        btnSend.contentDescription = getString(R.string.send)
        val tintColor = if (hasContent) {
            ContextCompat.getColor(this, R.color.primary)
        } else {
            ContextCompat.getColor(this, R.color.hint_text)
        }
        btnSend.setColorFilter(
            tintColor,
            PorterDuff.Mode.SRC_IN
        )
    }

    private fun updateSearchChip(chip: TextView, enabled: Boolean) {
        val enabledColor = ContextCompat.getColor(this, R.color.primary)
        val disabledColor = ContextCompat.getColor(this, R.color.chip_text)
        if (enabled) {
            chip.setBackgroundResource(R.drawable.bg_chip_active_blue)
            chip.setTextColor(enabledColor)
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_outline_gray)
            chip.setTextColor(disabledColor)
        }
        chip.compoundDrawablesRelative.forEach { drawable ->
            drawable?.mutate()?.setTint(if (enabled) enabledColor else disabledColor)
        }
    }

    private fun updateDeepThinkingChip(chip: TextView, enabled: Boolean) {
        val enabledColor = ContextCompat.getColor(this, R.color.primary)
        val disabledColor = ContextCompat.getColor(this, R.color.chip_text)
        if (enabled) {
            chip.setBackgroundResource(R.drawable.bg_chip_active_blue)
            chip.setTextColor(enabledColor)
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_outline_gray)
            chip.setTextColor(disabledColor)
        }
        chip.compoundDrawablesRelative.forEach { drawable ->
            drawable?.mutate()?.setTint(if (enabled) enabledColor else disabledColor)
        }
    }

    private fun showRenameConversationDialog(conversationId: Int, currentTitle: String) {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_rename_conversation, null)
        val input = content.findViewById<EditText>(R.id.et_conversation_title).apply {
            setText(currentTitle)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            selectAll()
        }
        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        content.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        content.findViewById<TextView>(R.id.btn_save).setOnClickListener {
            val title = input.text?.toString()?.trim().orEmpty()
            if (title.isBlank()) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            chatManager.renameConversation(conversationId, title)
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setDimAmount(0.38f)
            input.requestFocus()
        }
        dialog.show()
    }

    private fun handleV2SystemAction(action: V2SystemAction) {
        when (action) {
            is V2SystemAction.OpenCalendarInsert -> openCalendarInsert(action)
            is V2SystemAction.ConfirmReminder -> confirmReminder(action)
        }
    }

    private fun openCalendarInsert(action: V2SystemAction.OpenCalendarInsert) {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, action.title)
            .putExtra(CalendarContract.Events.DESCRIPTION, action.description)
            .putExtra(CalendarContract.Events.EVENT_TIMEZONE, action.timeZone)
            .apply {
                action.beginTimeMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                action.endTimeMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            }
        runCatching {
            startActivity(intent)
        }.onFailure {
            Toast.makeText(this, "未找到可用日历应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmReminder(action: V2SystemAction.ConfirmReminder) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Toast.makeText(this, "提醒已创建：${action.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateModeUi(
        mode: ChatMode,
        chipWebSearch: TextView,
        chipRagSettings: TextView,
        tvEmptyTitle: TextView,
        tvModeHint: TextView,
        hasMessages: Boolean
    ) {
        val isRag = mode == ChatMode.RAG
        val previousChipMode = lastModeChipIsRag
        if (previousChipMode != isRag) {
            updateModeChipVisibility(
                showing = if (isRag) chipRagSettings else chipWebSearch,
                hiding = if (isRag) chipWebSearch else chipRagSettings,
                animate = previousChipMode != null
            )
            lastModeChipIsRag = isRag
        }
        if (!hasMessages) {
            tvEmptyTitle.setText(if (isRag) R.string.empty_chat_hint_rag else R.string.empty_chat_hint_chat)
            tvModeHint.setText(if (isRag) R.string.mode_hint_rag else R.string.mode_hint_chat)
            MotionUtils.crossfadeText(tvEmptyTitle, tvModeHint)
            val animate = lastDisplayedMode != null && lastDisplayedMode != mode
            modeSwitch.select(mode, animate = animate)
            lastDisplayedMode = mode
        }
    }

    private fun updateModeChipVisibility(showing: TextView, hiding: TextView, animate: Boolean) {
        showing.animate().cancel()
        hiding.animate().cancel()
        showing.translationX = 0f
        showing.translationY = 0f
        hiding.translationX = 0f
        hiding.translationY = 0f

        if (!animate || !MotionUtils.animationsEnabled()) {
            showing.visibility = View.VISIBLE
            showing.alpha = 1f
            showing.scaleX = 1f
            showing.scaleY = 1f
            hiding.visibility = View.INVISIBLE
            hiding.alpha = 1f
            hiding.scaleX = 1f
            hiding.scaleY = 1f
            return
        }

        if (showing.visibility != View.VISIBLE) {
            showing.alpha = 0f
            showing.scaleX = 0.985f
            showing.scaleY = 0.985f
            showing.visibility = View.VISIBLE
        }
        showing.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(MotionTokens.MICRO_MILLIS)
            .setInterpolator(MotionUtils.easeOut)
            .start()

        if (hiding.visibility == View.VISIBLE) {
            hiding.animate()
                .alpha(0f)
                .scaleX(0.985f)
                .scaleY(0.985f)
                .setDuration(120L)
                .setInterpolator(MotionUtils.easeOutSoft)
                .withEndAction {
                    hiding.visibility = View.INVISIBLE
                    hiding.alpha = 1f
                    hiding.scaleX = 1f
                    hiding.scaleY = 1f
                }
                .start()
        } else {
            hiding.visibility = View.INVISIBLE
            hiding.alpha = 1f
            hiding.scaleX = 1f
            hiding.scaleY = 1f
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        private const val NEAR_BOTTOM_THRESHOLD_PX = 220
    }
}
