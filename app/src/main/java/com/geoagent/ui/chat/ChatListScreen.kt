package com.geoagent.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.domain.model.Conversation
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.BrandSoft
import com.geoagent.ui.theme.Surface
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import com.geoagent.ui.theme.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (Int) -> Unit,
    onNewChat: () -> Unit,
    conversations: List<Conversation> = emptyList()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geo-Agent", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChat,
                shape = RoundedCornerShape(100.dp),
                containerColor = BrandPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新对话", tint = White)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无对话", color = TextMuted, fontSize = 16.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(conversations) { conv ->
                    ChatListItem(
                        conversation = conv,
                        onClick = { onChatClick(conv.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatListItem(
    conversation: Conversation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.ChatBubble, null, modifier = Modifier.size(18.dp), tint = TextMuted)
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                conversation.title ?: "新对话",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp, color = TextPrimary
            )
            Text(
                conversation.lastMessage,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp, color = TextMuted
            )
        }
    }
}