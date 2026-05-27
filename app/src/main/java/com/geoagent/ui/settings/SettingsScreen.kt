package com.geoagent.ui.settings

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
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.ui.components.SettingsCard
import com.geoagent.ui.components.SettingsNavRow
import com.geoagent.ui.components.SettingsSectionTitle
import com.geoagent.ui.components.UserAvatar
import com.geoagent.ui.theme.Background
import com.geoagent.ui.theme.Destructive
import com.geoagent.ui.theme.Surface
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToKnowledgeBase: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadUser() }
    LaunchedEffect(state.logoutSuccess) { if (state.logoutSuccess) onLogout() }
    LaunchedEffect(state.error) { state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                state.user?.let { user ->
                    SettingsCard(Modifier.padding(top = 8.dp)) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                displayName = user.full_name ?: user.username,
                                remoteUrl = user.avatar_url,
                                localUri = state.localAvatarUri,
                                sizeDp = 48
                            )
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text(
                                    user.full_name ?: user.username,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(user.email, fontSize = 13.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }

            item { SettingsSectionTitle("账户") }
            item {
                SettingsCard {
                    SettingsNavRow(
                        icon = Icons.Filled.Lock,
                        title = "账号与安全",
                        onClick = onNavigateToAccount
                    )
                }
            }

            item { SettingsSectionTitle("知识库") }
            item {
                SettingsCard {
                    SettingsNavRow(
                        icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                        title = "知识库管理",
                        value = "文档与集合",
                        showDivider = false,
                        onClick = onNavigateToKnowledgeBase
                    )
                }
            }

            item { SettingsSectionTitle("应用") }
            item {
                SettingsCard {
                    SettingsNavRow(
                        icon = Icons.Outlined.Palette,
                        title = "外观",
                        value = state.themeMode.label,
                        showDivider = false,
                        onClick = onNavigateToAppearance
                    )
                }
            }

            item {
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { viewModel.logout() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Surface,
                        contentColor = Destructive
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("退出登录", color = Destructive, fontWeight = FontWeight.Medium)
                }
            }

            item {
                Text(
                    "Geo-Agent · 地质文献智能助手",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
