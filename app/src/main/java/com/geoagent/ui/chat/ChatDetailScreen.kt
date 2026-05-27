package com.geoagent.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.domain.model.ChatMode
import com.geoagent.domain.model.Message
import com.geoagent.ui.chat.components.ChatMessageBubble
import com.geoagent.ui.chat.components.DeepSeekChatComposer
import com.geoagent.ui.chat.components.DeepSeekModeSwitch
import com.geoagent.ui.chat.components.StreamingPulseIndicator
import com.geoagent.ui.theme.Background
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.Surface
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: Int,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    viewModel: ChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val modeHint = when (uiState.currentMode) {
        ChatMode.CHAT -> "适合日常问答，即时响应"
        ChatMode.RAG -> "结合个人知识库检索，回答附参考来源"
    }

    val showThinkingPulse = uiState.isLoading &&
        uiState.messages.none { it.role == Message.ROLE_ASSISTANT && it.content.isNotBlank() }

    val isEmptyChat = uiState.messages.isEmpty() && !uiState.isLoading

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "打开菜单", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.resetChat()
                        onNewChat()
                    }) {
                        Icon(Icons.Outlined.EditNote, contentDescription = "新对话", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Background)
        ) {
            if (isEmptyChat) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isEmptyChat,
                    enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.spring(stiffness = 300f)) +
                            androidx.compose.animation.slideInVertically { it / 3 },
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Geo-Agent",
                                style = MaterialTheme.typography.headlineSmall,
                                color = BrandPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "使用快速模式开始对话",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(28.dp))
                            DeepSeekModeSwitch(
                                selected = uiState.currentMode,
                                onSelect = { viewModel.setMode(it) },
                                modifier = Modifier.fillMaxWidth(0.92f)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = modeHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                        itemsIndexed(
                            items = uiState.messages,
                            key = { _, msg -> "${msg.role}_${msg.timestamp}" }
                        ) { index, message ->
                            val isLatest = index == uiState.messages.lastIndex
                            ChatMessageBubble(
                                message = message,
                                isLatest = isLatest,
                                isStreaming = isLatest && uiState.isLoading && message.role == Message.ROLE_ASSISTANT,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                    if (showThinkingPulse) {
                        item(key = "thinking-pulse") {
                            StreamingPulseIndicator(
                                statusMessage = uiState.statusMessage ?: "正在生成回答…"
                            )
                        }
                    }
                }

                // Auto-scroll: only trigger when a new message is added or the loading state changes.
                // Do NOT react to content changes within a message to avoid jitter during streaming.
                LaunchedEffect(uiState.messages.size, showThinkingPulse) {
                    val itemCount = uiState.messages.size + if (showThinkingPulse) 1 else 0
                    if (itemCount > 0) {
                        listState.scrollToItem(itemCount - 1)
                    }
                }
            }

            DeepSeekChatComposer(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                selectedMode = uiState.currentMode,
                webSearchEnabled = uiState.webSearchEnabled,
                onWebSearchToggle = {
                    viewModel.setWebSearchEnabled(!uiState.webSearchEnabled)
                }
            )
        }
    }
}
