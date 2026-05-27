package com.geoagent.ui.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.domain.model.ChatMode
import com.geoagent.ui.theme.BrandBorder
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.BrandSoft
import com.geoagent.ui.theme.InputSurface
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import com.geoagent.ui.theme.White

@Composable
fun DeepSeekChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    selectedMode: ChatMode,
    webSearchEnabled: Boolean,
    onWebSearchToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        color = InputSurface,
        border = BorderStroke(1.dp, BrandBorder.copy(alpha = 0.28f))
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        "发消息或按住发送键提问…",
                        color = TextMuted,
                        fontSize = 16.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, color = TextPrimary),
                minLines = 1,
                maxLines = 6,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = BrandPrimary
                )
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ComposerFeatureChip(
                    label = "联网搜索",
                    icon = { Icon(Icons.Outlined.Language, null, tint = if (webSearchEnabled) BrandPrimary else TextMuted, modifier = Modifier.size(16.dp)) },
                    selected = webSearchEnabled,
                    enabled = selectedMode == ChatMode.CHAT,
                    onClick = onWebSearchToggle
                )

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val sendScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.92f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(stiffness = 400f, dampingRatio = 0.5f),
                    label = "send_scale"
                )
                val sendBgColor by animateColorAsState(
                    targetValue = if (value.isNotBlank()) BrandPrimary else BrandSoft,
                    animationSpec = androidx.compose.animation.core.tween(200),
                    label = "send_bg_color"
                )

                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank(),
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .scale(sendScale)
                        .background(sendBgColor)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = if (value.isNotBlank()) White else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerFeatureChip(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        !enabled -> BrandSoft.copy(alpha = 0.4f)
        selected -> BrandSoft
        else -> Color.Transparent
    }
    val borderColor = when {
        !enabled -> BrandBorder.copy(alpha = 0.2f)
        selected -> BrandPrimary.copy(alpha = 0.5f)
        else -> BrandBorder.copy(alpha = 0.35f)
    }
    val textColor = when {
        !enabled -> TextMuted.copy(alpha = 0.5f)
        selected -> BrandPrimary
        else -> TextPrimary
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
        color = bg,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(label, fontSize = 14.sp, color = textColor, fontWeight = MaterialTheme.typography.labelMedium.fontWeight)
        }
    }
}
