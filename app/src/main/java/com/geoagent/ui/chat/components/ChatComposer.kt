package com.geoagent.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.ui.theme.BrandBorder
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.BrandSoft
import com.geoagent.ui.theme.InputSurface
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import com.geoagent.ui.theme.White

@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String = "发消息，按发送键提问…"
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(8.dp, RoundedCornerShape(28.dp), clip = false),
        shape = RoundedCornerShape(28.dp),
        color = InputSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        placeholder,
                        color = TextMuted,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    color = TextPrimary
                ),
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

            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (value.isNotBlank()) BrandPrimary else BrandSoft)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (value.isNotBlank()) White else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
