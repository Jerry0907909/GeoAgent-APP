package com.geoagent.ui.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.geoagent.ui.theme.BrandPrimary
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.geoagent.ui.theme.TextMuted

/**
 * A smooth thinking wave indicator with spring-physics-like animation.
 * Replaces the rigid LinearEasing with a more organic, breathing feel.
 */
@Composable
fun StreamingPulseIndicator(
    statusMessage: String,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "wave")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) { index ->
                // Use a sine-wave based animation for more natural feel
                val progress by infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing, delayMillis = index * 100),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "wave_bar_$index"
                )

                // Map progress to a sine wave for organic motion
                val sine = kotlin.math.sin(progress.toDouble() * kotlin.math.PI).toFloat()
                val scaleY = 0.4f + (sine * 0.6f).coerceIn(0.4f, 1f)
                val alpha = 0.3f + (sine * 0.7f).coerceIn(0.3f, 1f)

                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .scale(scaleY = scaleY, scaleX = 1f)
                        .alpha(alpha)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BrandPrimary)
                )
            }
        }

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}
