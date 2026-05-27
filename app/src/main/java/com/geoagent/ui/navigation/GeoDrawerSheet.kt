package com.geoagent.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.data.api.UserResponse
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.domain.model.Conversation
import com.geoagent.ui.components.UserAvatar
import com.geoagent.ui.theme.Background
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.BrandSoft
import com.geoagent.ui.theme.DividerLine
import org.koin.androidx.compose.get
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import com.geoagent.ui.theme.TextTertiary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

data class ConversationGroup(
    val label: String,
    val items: List<Conversation>
)

private sealed interface DrawerListEntry {
    data class Header(val label: String) : DrawerListEntry
    data class ConversationItem(val conversation: Conversation) : DrawerListEntry
}

@Composable
fun GeoDrawerSheet(
    conversations: List<Conversation>,
    isLoading: Boolean,
    selectedConversationId: Int?,
    user: UserResponse?,
    onConversationClick: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userPrefs: UserPrefsDataStore = get()
    val localAvatarUri by userPrefs.localAvatarUri.collectAsState(initial = null)

    val groups = remember(conversations) { groupConversationsByDate(conversations) }
    val flatEntries = remember(groups) {
        groups.flatMap { group ->
            buildList {
                add(DrawerListEntry.Header(group.label))
                group.items.forEach { add(DrawerListEntry.ConversationItem(it)) }
            }
        }
    }

    ModalDrawerSheet(
        modifier = modifier.width(320.dp),
        drawerContainerColor = Background
    ) {
        Column(Modifier.fillMaxHeight()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isLoading && conversations.isEmpty()) {
                    item(key = "loading") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = BrandPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                } else if (flatEntries.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "暂无对话记录",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                } else {
                    items(
                        items = flatEntries,
                        key = { entry ->
                            when (entry) {
                                is DrawerListEntry.Header -> "h-${entry.label}"
                                is DrawerListEntry.ConversationItem -> "c-${entry.conversation.id}"
                            }
                        }
                    ) { entry ->
                        when (entry) {
                            is DrawerListEntry.Header -> {
                                Text(
                                    text = entry.label,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextTertiary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            is DrawerListEntry.ConversationItem -> {
                                DrawerConversationRow(
                                    conversation = entry.conversation,
                                    selected = entry.conversation.id == selectedConversationId,
                                    onClick = { onConversationClick(entry.conversation.id) }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = DividerLine)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    displayName = user?.full_name ?: user?.username,
                    remoteUrl = user?.avatar_url,
                    localUri = localAvatarUri,
                    sizeDp = 40
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = user?.full_name?.takeIf { it.isNotBlank() } ?: user?.username ?: "未登录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "设置与知识库",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Icon(Icons.Filled.MoreHoriz, contentDescription = "设置", tint = TextMuted, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun DrawerConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) BrandSoft else Background
    Text(
        text = conversation.title?.takeIf { it.isNotBlank() } ?: "新对话",
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) BrandPrimary else TextPrimary,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

fun groupConversationsByDate(conversations: List<Conversation>): List<ConversationGroup> {
    if (conversations.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)

    val todayList = mutableListOf<Conversation>()
    val yesterdayList = mutableListOf<Conversation>()
    val earlierList = mutableListOf<Conversation>()

    conversations.forEach { conv ->
        val date = parseConversationDate(conv.updatedAt, zone)
        when (date) {
            today -> todayList.add(conv)
            yesterday -> yesterdayList.add(conv)
            else -> earlierList.add(conv)
        }
    }

    return buildList {
        if (todayList.isNotEmpty()) add(ConversationGroup("今天", todayList))
        if (yesterdayList.isNotEmpty()) add(ConversationGroup("昨天", yesterdayList))
        if (earlierList.isNotEmpty()) add(ConversationGroup("更早", earlierList))
    }
}

// LocalDate.EPOCH is JDK 9+ and not available on Android runtime.
private val FallbackConversationDate = LocalDate.of(1970, 1, 1)

private fun parseConversationDate(raw: String, zone: ZoneId): LocalDate {
    if (raw.isBlank()) return FallbackConversationDate
    return try {
        Instant.parse(raw).atZone(zone).toLocalDate()
    } catch (_: DateTimeParseException) {
        try {
            java.time.OffsetDateTime.parse(raw).atZoneSameInstant(zone).toLocalDate()
        } catch (_: Exception) {
            FallbackConversationDate
        }
    }
}
