package com.geoagent.ui.chat.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.domain.model.ChatMode
import com.geoagent.ui.theme.BrandBorder
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.SegmentTrack
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import com.geoagent.ui.theme.White

/**
 * DeepSeek-style segmented control with sliding pill indicator.
 */
@Composable
fun DeepSeekModeSwitch(
    selected: ChatMode,
    onSelect: (ChatMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        "智能对话" to ChatMode.CHAT,
        "知识库 RAG" to ChatMode.RAG
    )
    val selectedIndex = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .shadow(2.dp, RoundedCornerShape(50), clip = false)
            .clip(RoundedCornerShape(50))
            .background(SegmentTrack)
            .border(1.dp, BrandBorder.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(3.dp)
    ) {
        val tabWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "modeIndicator"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .shadow(1.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(White)
                .border(1.dp, BrandBorder.copy(alpha = 0.45f), RoundedCornerShape(50))
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEach { (label, mode) ->
                val isSelected = mode == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) BrandPrimary else TextPrimary.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}
