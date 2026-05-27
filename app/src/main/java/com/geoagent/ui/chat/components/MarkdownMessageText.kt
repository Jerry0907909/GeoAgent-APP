package com.geoagent.ui.chat.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.TextPrimary
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Renders markdown text with consistent, body-level typography.
 * All headings are downscaled to avoid large, jarring fonts next to normal text.
 */
@Composable
fun MarkdownMessageText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    if (markdown.isBlank()) return

    Markdown(
        content = markdown,
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(
            text = TextPrimary,
            codeText = TextPrimary,
            linkText = BrandPrimary
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            h2 = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            h3 = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            ),
            h4 = MaterialTheme.typography.bodyMedium,
            h5 = MaterialTheme.typography.bodyMedium,
            h6 = MaterialTheme.typography.bodyMedium,
            paragraph = MaterialTheme.typography.bodyMedium,
            text = MaterialTheme.typography.bodyMedium
        )
    )
}
