package com.geoagent.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.model.SearchSource
import com.geoagent.model.isKnowledgeBaseSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSourceCard(
    sources: List<SearchSource>,
    modifier: Modifier = Modifier,
    onSourceClick: (SearchSource) -> Unit = {}
) {
    if (sources.isEmpty()) return

    var showSources by remember { mutableStateOf(false) }
    val colors = searchSourceColors()
    val sourceKind = sources.sourceKindLabel()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable { showSources = true },
            shape = RoundedCornerShape(100.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, top = 6.dp, end = 13.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceBadgeRow(count = sources.size.coerceAtMost(4))
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = "${sources.size} 个$sourceKind",
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    SearchSourceSheet(
        sources = sources,
        visible = showSources,
        onDismiss = { showSources = false },
        onSourceClick = onSourceClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSourceSheet(
    sources: List<SearchSource>,
    visible: Boolean,
    onDismiss: () -> Unit,
    onSourceClick: (SearchSource) -> Unit = {}
) {
    if (!visible || sources.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = searchSourceColors()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.sheet,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        SourceSheet(sources = sources, colors = colors, onSourceClick = onSourceClick)
    }
}

@Composable
private fun SourceSheet(
    sources: List<SearchSource>,
    colors: SearchSourceColors,
    onSourceClick: (SearchSource) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = if (sources.all { it.isKnowledgeBaseSource() }) "知识库来源" else "搜索结果",
            color = colors.primaryText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        sources.forEachIndexed { index, source ->
            SearchSourceRow(index = index + 1, source = source, colors = colors, onSourceClick = onSourceClick)
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun SearchSourceRow(
    index: Int,
    source: SearchSource,
    colors: SearchSourceColors,
    onSourceClick: (SearchSource) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val canOpenKnowledgeBase = source.isKnowledgeBaseSource() && !source.documentId.isNullOrBlank()
    val clickable = canOpenKnowledgeBase || source.url.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable) {
                if (canOpenKnowledgeBase) {
                    onSourceClick(source)
                } else if (source.url.isNotBlank()) {
                    uriHandler.openUri(source.url)
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceIcon(colors)
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = source.sourceLabel(),
                color = colors.primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = source.displayDate(),
                color = colors.mutedText,
                fontSize = 13.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = colors.badge,
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = index.toString(),
                        color = colors.secondaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = source.title.ifBlank { source.url },
            color = colors.primaryText,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val excerpt = source.content.cleanExcerpt()
        if (excerpt.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = excerpt,
                color = colors.secondaryText,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SourceIcon(colors: SearchSourceColors) {
    SourceBadgeDot(size = 28, colors = colors)
}

@Composable
private fun SourceBadgeRow(count: Int) {
    val colors = searchSourceColors()
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(count.coerceAtLeast(1)) {
            SourceBadgeDot(size = 20, colors = colors)
        }
    }
}

@Composable
private fun SourceBadgeDot(size: Int, colors: SearchSourceColors) {
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.badge,
        modifier = Modifier.size(size.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size((size * 0.58f).dp)) {
                val stroke = Stroke(width = 1.45.dp.toPx(), cap = StrokeCap.Round)
                val linkSize = Size(width = this.size.width * 0.56f, height = this.size.height * 0.34f)
                val radius = CornerRadius(linkSize.height / 2f, linkSize.height / 2f)
                rotate(degrees = -35f) {
                    drawRoundRect(
                        color = colors.icon,
                        topLeft = Offset(x = this.size.width * 0.06f, y = this.size.height * 0.33f),
                        size = linkSize,
                        cornerRadius = radius,
                        style = stroke
                    )
                    drawRoundRect(
                        color = colors.icon,
                        topLeft = Offset(x = this.size.width * 0.38f, y = this.size.height * 0.33f),
                        size = linkSize,
                        cornerRadius = radius,
                        style = stroke
                    )
                }
            }
        }
    }
}

@Composable
private fun searchSourceColors(): SearchSourceColors {
    return if (isSystemInDarkTheme()) {
        SearchSourceColors(
            sheet = Color(0xFF1A1D23),
            card = Color(0xFF252830),
            badge = Color(0xFF2D3139),
            border = Color(0xFF2D3139),
            primaryText = Color(0xFFF3F4F6),
            secondaryText = Color(0xFFC8CDD6),
            mutedText = Color(0xFF9CA3AF),
            icon = Color(0xFF9CA3AF)
        )
    } else {
        SearchSourceColors(
            sheet = Color.White,
            card = Color.White,
            badge = Color(0xFFF3F5F7),
            border = Color(0xFFE1E5EC),
            primaryText = Color(0xFF0F1115),
            secondaryText = Color(0xFF81858C),
            mutedText = Color(0xFF9AA0A6),
            icon = Color(0xFF8F969E)
        )
    }
}

private data class SearchSourceColors(
    val sheet: Color,
    val card: Color,
    val badge: Color,
    val border: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val icon: Color
)

private fun String.toDomain(): String =
    removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")

private fun List<SearchSource>.sourceKindLabel(): String =
    if (all { it.isKnowledgeBaseSource() }) "知识库" else "网页"

private fun SearchSource.sourceLabel(): String =
    if (isKnowledgeBaseSource()) "知识库" else url.toDomain().ifBlank { "网页" }

private fun SearchSource.displayDate(): String {
    if (isKnowledgeBaseSource()) return "本地文档"
    publishedDate?.normalizeDate()?.let { return it }
    val joined = "$title $content $url"
    val match = Regex("""20\d{2}[-/.年]\d{1,2}(?:[-/.月]\d{1,2})?""").find(joined)?.value
    return match?.normalizeDate() ?: "日期未知"
}

private fun String.normalizeDate(): String? {
    val cleaned = trim()
        .replace("年", "/")
        .replace("月", "/")
        .replace("日", "")
        .replace("-", "/")
        .replace(".", "/")
    val parts = cleaned.split("/")
        .filter { it.isNotBlank() }
        .take(3)
    if (parts.size < 2) return null
    val year = parts[0]
    val month = parts[1].padStart(2, '0')
    val day = parts.getOrNull(2)?.padStart(2, '0')
    return if (day == null) "$year/$month" else "$year/$month/$day"
}

private fun String.cleanExcerpt(): String =
    replace(Regex("""\s+"""), " ")
        .trim()
