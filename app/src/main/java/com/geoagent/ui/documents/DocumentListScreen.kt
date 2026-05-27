package com.geoagent.ui.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.data.api.dto.DocumentDto
import com.geoagent.ui.components.CorporateConfirmDialog
import com.geoagent.ui.theme.BrandBorder
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.BrandSoft
import com.geoagent.ui.theme.Success
import com.geoagent.ui.theme.Surface
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import com.geoagent.ui.theme.White
import org.koin.androidx.compose.getViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    onBack: () -> Unit = {},
    onUploadClick: () -> Unit,
    onDocumentClick: (DocumentDto) -> Unit = {},
    viewModel: DocumentViewModel = getViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var deleteTarget by remember { mutableStateOf<DocumentDto?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadDocuments() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    if (deleteTarget != null) {
        CorporateConfirmDialog(
            title = "确认删除",
            message = "确定要删除「${deleteTarget?.name}」吗？此操作不可恢复。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                deleteTarget?.let { viewModel.deleteDocument(it.id) }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("知识库", fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onUploadClick, shape = RoundedCornerShape(100.dp), containerColor = BrandPrimary
            ) { Icon(Icons.Filled.Upload, null, tint = White) }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandPrimary)
            }
        } else if (uiState.documents.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无文档", color = TextMuted, fontSize = 16.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(uiState.documents) { doc ->
                    DocumentRow(
                        document = doc,
                        onClick = { onDocumentClick(doc) },
                        onDelete = { deleteTarget = doc }
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentRow(document: DocumentDto, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(BrandSoft, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.InsertDriveFile, null, tint = BrandPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(document.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, color = TextPrimary)
                val meta = buildString {
                    if (document.chunks > 0) append("${document.chunks} 片段")
                    if (document.created_at.isNotBlank() && document.created_at != "-") {
                        if (isNotEmpty()) append(" · ")
                        append(document.created_at)
                    }
                    if (document.collection.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(document.collection)
                    }
                }
                Text(
                    meta.ifBlank { document.type },
                    fontSize = 13.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Delete, null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    onBack: () -> Unit,
    viewModel: DocumentViewModel = getViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedUri = it
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) selectedFileName = c.getString(nameIdx)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("上传文档", fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp).clickable { launcher.launch("*/*") },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = androidx.compose.foundation.BorderStroke(2.dp, BrandBorder.copy(alpha = 0.5f))
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.Upload, null, tint = BrandPrimary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("点击上传文档", fontSize = 16.sp, color = TextPrimary)
                    Text("支持 PDF, Word, TXT", fontSize = 13.sp, color = TextMuted)
                }
            }
            Spacer(Modifier.height(24.dp))

            selectedFileName?.let { name ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.InsertDriveFile, null, tint = BrandPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(name, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(color = BrandPrimary)
            }

            if (uiState.uploadSuccess.isNotEmpty()) {
                Text(uiState.uploadSuccess, color = Success, fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { selectedUri?.let { viewModel.uploadFile(context, it) } },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                enabled = selectedUri != null && !uiState.isLoading,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                Text("上传文件", color = White, fontSize = 16.sp)
            }
        }
    }
}