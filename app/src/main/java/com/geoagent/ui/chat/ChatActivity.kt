package com.geoagent.ui.chat

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geoagent.R
import com.geoagent.domain.model.ChatMode
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.domain.repository.ChatRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.documents.DocumentListActivity
import com.geoagent.ui.settings.SettingsActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ChatActivity : AppCompatActivity() {

    private val chatRepository: ChatRepository by inject()
    private val authRepository: AuthRepository by inject()

    private lateinit var chatManager: ChatManager
    private lateinit var messageAdapter: ChatMessageAdapter
    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var markwon: Markwon
    private lateinit var modeSwitch: DeepSeekModeSwitch
    private lateinit var etMessage: EditText
    private lateinit var btnSend: FloatingActionButton

    private var pendingImageBase64: String? = null
    private var syncingSliders = false
    private var lastDisplayedMode: ChatMode? = null

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
        chatManager = ChatManager(lifecycleScope, chatRepository, authRepository)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val rvMessages = findViewById<RecyclerView>(R.id.rv_messages)
        val rvConversations = findViewById<RecyclerView>(R.id.rv_conversations)
        val emptyLayout = findViewById<View>(R.id.layout_empty_chat)
        val tvEmptyTitle = findViewById<TextView>(R.id.tv_empty_title)
        val tvModeHint = findViewById<TextView>(R.id.tv_mode_hint)
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        val btnModeChat = findViewById<TextView>(R.id.btn_mode_chat)
        val btnModeRag = findViewById<TextView>(R.id.btn_mode_rag)
        val chipWebSearch = findViewById<Chip>(R.id.chip_web_search)
        val chipImage = findViewById<Chip>(R.id.chip_image)
        val chipRagSettings = findViewById<Chip>(R.id.chip_rag_settings)
        val cardRagSettings = findViewById<MaterialCardView>(R.id.card_rag_settings)
        val sliderTopK = findViewById<Slider>(R.id.slider_top_k)
        val sliderMinRelevance = findViewById<Slider>(R.id.slider_min_relevance)

        modeSwitch = DeepSeekModeSwitch(
            host = findViewById(R.id.mode_switch_container),
            indicator = findViewById(R.id.mode_switch_indicator),
            chatTab = btnModeChat,
            ragTab = btnModeRag
        )

        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
            refreshConversations()
        }
        findViewById<ImageButton>(R.id.btn_new_chat).setOnClickListener {
            chatManager.resetChat()
        }

        messageAdapter = ChatMessageAdapter()
        rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.adapter = messageAdapter

        conversationAdapter = ConversationAdapter { conversation ->
            chatManager.loadConversation(conversation.id)
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        rvConversations.layoutManager = LinearLayoutManager(this)
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

        chipWebSearch.setOnCheckedChangeListener { _, checked -> chatManager.setWebSearchEnabled(checked) }
        chipImage.setOnClickListener { imagePicker.launch("image/*") }
        chipRagSettings.setOnClickListener {
            val expanded = cardRagSettings.visibility != View.VISIBLE
            cardRagSettings.visibility = if (expanded) View.VISIBLE else View.GONE
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
                btnSend.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_press))
                return@setOnClickListener
            }
            btnSend.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_press))
            chatManager.sendMessage(text.ifEmpty { "请分析这张图片" }, pendingImageBase64)
            etMessage.text?.clear()
            pendingImageBase64 = null
            updateSendButtonState()
        }

        findViewById<MaterialButton>(R.id.btn_drawer_documents).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, DocumentListActivity::class.java))
            TransitionHelper.forward(this)
        }
        findViewById<MaterialButton>(R.id.btn_drawer_settings).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
            TransitionHelper.forward(this)
        }

        updateSendButtonState()

        lifecycleScope.launch {
            chatManager.uiState.collectLatest { state ->
                messageAdapter.submit(state.messages, markwon)
                val hasMessages = state.messages.isNotEmpty()
                updateEmptyState(emptyLayout, hasMessages)
                rvMessages.visibility = if (hasMessages) View.VISIBLE else View.INVISIBLE
                if (hasMessages) rvMessages.scrollToPosition(state.messages.size - 1)

                val status = when {
                    state.isLoading && !state.statusMessage.isNullOrBlank() -> state.statusMessage
                    state.isLoading -> if (state.currentMode == ChatMode.RAG) "正在生成回答…" else "正在生成对话…"
                    else -> null
                }
                if (status != null) {
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = status
                    if (tvStatus.animation == null) {
                        tvStatus.startAnimation(AnimationUtils.loadAnimation(this@ChatActivity, R.anim.pulse_alpha))
                    }
                } else {
                    tvStatus.clearAnimation()
                    tvStatus.visibility = View.GONE
                }

                state.error?.let {
                    Toast.makeText(this@ChatActivity, it, Toast.LENGTH_SHORT).show()
                    chatManager.clearError()
                }
                state.retrievalHint?.let {
                    Toast.makeText(this@ChatActivity, it, Toast.LENGTH_LONG).show()
                    chatManager.clearRetrievalHint()
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

                updateModeUi(state.currentMode, chipWebSearch, chipRagSettings, tvEmptyTitle, tvModeHint, hasMessages)
                chipWebSearch.isChecked = state.webSearchEnabled
                cardRagSettings.visibility =
                    if (state.ragSettingsExpanded && state.currentMode == ChatMode.RAG) View.VISIBLE else View.GONE

                syncingSliders = true
                sliderTopK.value = state.ragTopK.toFloat()
                sliderMinRelevance.value = state.ragMinRelevanceScore
                syncingSliders = false
            }
        }
    }

    private fun updateEmptyState(emptyLayout: View, hasMessages: Boolean) {
        emptyLayout.visibility = if (hasMessages) View.GONE else View.VISIBLE
    }

    private fun updateSendButtonState() {
        val hasContent = etMessage.text?.toString()?.trim()?.isNotEmpty() == true || pendingImageBase64 != null
        btnSend.isEnabled = hasContent
        val tintColor = ContextCompat.getColor(
            this,
            if (hasContent) R.color.primary else R.color.deepseek_send_disabled
        )
        btnSend.backgroundTintList = android.content.res.ColorStateList.valueOf(tintColor)
        btnSend.setColorFilter(
            ContextCompat.getColor(this, R.color.on_primary),
            PorterDuff.Mode.SRC_IN
        )
    }

    private fun refreshConversations() {
        lifecycleScope.launch {
            chatRepository.listConversations().fold(
                onSuccess = { conversationAdapter.submit(it) },
                onFailure = { Toast.makeText(this@ChatActivity, it.message ?: "加载失败", Toast.LENGTH_SHORT).show() }
            )
        }
    }

    private fun updateModeUi(
        mode: ChatMode,
        chipWebSearch: Chip,
        chipRagSettings: Chip,
        tvEmptyTitle: TextView,
        tvModeHint: TextView,
        hasMessages: Boolean
    ) {
        val isRag = mode == ChatMode.RAG
        chipWebSearch.visibility = if (isRag) View.GONE else View.VISIBLE
        chipRagSettings.visibility = if (isRag) View.VISIBLE else View.GONE
        if (!hasMessages) {
            tvEmptyTitle.setText(if (isRag) R.string.empty_chat_hint_rag else R.string.empty_chat_hint_chat)
            tvModeHint.setText(if (isRag) R.string.mode_hint_rag else R.string.mode_hint_chat)
            val animate = lastDisplayedMode != null && lastDisplayedMode != mode
            modeSwitch.select(mode, animate = animate)
            lastDisplayedMode = mode
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        TransitionHelper.backward(this)
    }

    override fun onDestroy() {
        chatManager.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}
