package com.geoagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geoagent.ui.theme.CardSurface
import com.geoagent.ui.theme.DividerLine
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary

@Composable
fun SettingsSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
    ) {
        content()
    }
}

@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            value?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 52.dp),
                color = DividerLine,
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
fun SettingsSpacer() {
    Spacer(Modifier.height(8.dp))
}
