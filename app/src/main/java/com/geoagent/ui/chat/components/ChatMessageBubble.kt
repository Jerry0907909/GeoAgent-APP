package com.geoagent.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geoagent.data.api.dto.SourceDto
import com.geoagent.domain.model.Message
import com.geoagent.ui.theme.BrandBorder
import com.geoagent.ui.theme.InputSurface
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import com.geoagent.ui.theme.UserBubbleBg
import com.geoagent.ui.theme.UserBubbleBorder

@Composable
fun ChatMessageBubble(
    message: Message,
    isLatest: Boolean = false,
    isStreaming: Boolean = false,
    onCopy: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null
) {
    val isUser = message.role == Message.ROLE_USER

    // Animate each bubble only once on first appearance.
    // Use message.timestamp as part of the key so new messages animate,
    // but the animation doesn't re-trigger when content updates during streaming.
    var hasAnimated by remember(message.timestamp) { mutableStateOf(false) }
    var visible by remember(message.timestamp) { mutableStateOf(false) }

    LaunchedEffect(message.timestamp) {
        if (!hasAnimated) {
            visible = true
            hasAnimated = true
        }
    }

    val enterAnimation = remember(message.timestamp) {
        if (isUser) {
            slideInHorizontally(
                animationSpec = spring(stiffness = 300f, dampingRatio = 0.85f),
                initialOffsetX = { it }
            ) + fadeIn(animationSpec = spring(stiffness = 300f))
        } else {
            slideInHorizontally(
                animationSpec = spring(stiffness = 300f, dampingRatio = 0.85f),
                initialOffsetX = { -it }
            ) + fadeIn(animationSpec = spring(stiffness = 300f))
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = enterAnimation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (isUser) {
                Surface(
                    modifier = Modifier.widthIn(max = 300.dp),
                    shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                    color = UserBubbleBg,
                    border = BorderStroke(1.dp, UserBubbleBorder)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Directly render the content without any per-character animation.
                    // The SSE streaming is already handled by the ViewModel updating state.
                    if (isLatest && isStreaming) {
                        StreamingMessageText(markdown = message.content)
                    } else {
                        MarkdownMessageText(markdown = message.content)
                    }

                    if (message.sources.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "参考来源",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        SourcesChipRow(sources = message.sources)
                    }

                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { onCopy?.invoke() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "复制", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onRegenerate?.invoke() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Refresh, contentDescription = "重新生成", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourcesChipRow(sources: List<SourceDto>) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(sources) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)) +
                expandVertically(animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.forEachIndexed { index, source ->
                SourceCard(source = source, index = index)
            }
        }
    }
}

@Composable
private fun SourceCard(source: SourceDto, index: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = InputSurface,
        border = BorderStroke(1.dp, BrandBorder.copy(alpha = 0.3f)),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            val title = source.source.ifBlank { "来源 ${index + 1}" }
            val typeLabel = when (source.type) {
                "web" -> "网络"
                "document" -> "文献"
                else -> source.type
            }
            Text(
                text = "【$typeLabel】$title",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
            if (!source.url.isNullOrBlank()) {
                Text(source.url, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            source.relevance_score?.let { score ->
                Text(
                    "相关度 ${"%.0f".format(score * 100)}%",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (source.content.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    source.content.take(220).let { if (source.content.length > 220) "$it…" else it },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
    }
}
