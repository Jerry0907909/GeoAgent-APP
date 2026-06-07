package com.geoagent.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.model.SearchSource

@Composable
fun SearchSourceCard(
    sources: List<SearchSource>,
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "来源",
            color = Color(0xFF757575),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        sources.forEachIndexed { index, source ->
            SearchSourceRow(index = index + 1, source = source)
        }
    }
}

@Composable
private fun SearchSourceRow(
    index: Int,
    source: SearchSource
) {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = source.url.isNotBlank()) {
                uriHandler.openUri(source.url)
            },
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF7FAFC),
        border = BorderStroke(1.dp, Color(0xFFE3E8EF))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "[$index]",
                color = Color(0xFF1E88E5),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.title.ifBlank { source.url },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = source.url.toDomain(),
                    color = Color(0xFF757575),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun String.toDomain(): String =
    removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
