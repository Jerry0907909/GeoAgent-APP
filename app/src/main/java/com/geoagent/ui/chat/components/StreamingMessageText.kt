package com.geoagent.ui.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.geoagent.ui.theme.BrandPrimary

/**
 * Renders streaming text with a blinking cursor at the end.
 * The text itself appears smoothly as the ViewModel streams characters incrementally.
 * No artificial typing delay — that would conflict with the SSE streaming.
 */
@Composable
fun StreamingMessageText(
    markdown: String,
) {
    Row {
        if (markdown.isNotBlank()) {
            MarkdownMessageText(markdown = markdown)
        }
        // Blinking cursor at the end of streaming text
        BlinkingCursor()
    }
}

/**
 * A blinking cursor that mimics the terminal cursor.
 */
@Composable
private fun BlinkingCursor(
    blinkIntervalMillis: Int = 530
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(blinkIntervalMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Text(
        text = "▋",
        color = BrandPrimary,
        modifier = Modifier.alpha(alpha)
    )
}
