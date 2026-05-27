package com.geoagent.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.ui.chat.components.ChatComposer
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import com.geoagent.ui.theme.Surface
import org.koin.androidx.compose.getViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    viewModel: SearchViewModel = getViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("深度搜索", fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ChatComposer(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.reset()
                        viewModel.search(inputText)
                        inputText = ""
                    }
                },
                placeholder = "输入搜索问题..."
            )

            if (uiState.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = BrandPrimary)
            }

            when (uiState.currentPhase) {
                SearchPhase.IDLE -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Search, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("使用深度搜索获取精准答案", fontSize = 22.sp, color = TextMuted)
                        }
                    }
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.planQueries.isNotEmpty()) {
                            item { SectionCard("搜索计划") { uiState.planQueries.forEach { Text("- $it", fontSize = 14.sp, color = TextPrimary) } } }
                        }
                        if (uiState.searchResults.isNotEmpty()) {
                            item { SectionCard("搜索结果") { uiState.searchResults.forEach { Text("· $it", fontSize = 13.sp, color = TextMuted, maxLines = 2) } } }
                        }
                        if (uiState.extractedContent.isNotEmpty()) {
                            item { SectionCard("提取内容") { Text(uiState.extractedContent, fontSize = 14.sp, color = TextPrimary) } }
                        }
                        if (uiState.answer.isNotEmpty()) {
                            item { SectionCard("答案") { Text(uiState.answer, fontSize = 15.sp, lineHeight = 24.sp, color = TextPrimary) } }
                        }
                        if (uiState.citations.isNotEmpty()) {
                            item { SectionCard("引用来源") { uiState.citations.forEach { Text("· $it", fontSize = 12.sp, color = TextMuted) } } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface), elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}