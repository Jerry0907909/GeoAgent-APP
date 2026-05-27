package com.geoagent.ui.documents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.ui.chat.components.MarkdownMessageText
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.Surface
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import org.koin.androidx.compose.getViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    source: String,
    collection: String,
    onBack: () -> Unit,
    viewModel: DocumentViewModel = getViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val displayName = source.substringAfterLast('/').ifEmpty { source }

    LaunchedEffect(source, collection) {
        viewModel.loadDocumentContent(source, collection)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        displayName,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearDocumentContent()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            uiState.isContentLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandPrimary)
                }
            }
            uiState.documentContent != null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (collection.isNotBlank()) {
                        Text("知识库：$collection", fontSize = 13.sp, color = TextMuted)
                    }
                    MarkdownMessageText(
                        markdown = uiState.documentContent.orEmpty(),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
            else -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无内容", color = TextMuted)
                }
            }
        }
    }
}
