package com.geoagent.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.ui.components.SettingsCard
import com.geoagent.ui.components.SettingsSectionTitle
import com.geoagent.ui.theme.AppThemeMode
import com.geoagent.ui.theme.Background
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.Surface
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadUser() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("外观", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextPrimary) },
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
            item { SettingsSectionTitle("主题模式") }
            item {
                SettingsCard {
                    AppThemeMode.entries.forEachIndexed { index, mode ->
                        ThemeOptionRow(
                            label = mode.label,
                            selected = state.themeMode == mode,
                            showDivider = index < AppThemeMode.entries.lastIndex,
                            onSelect = { viewModel.setThemeMode(mode) }
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "切换后将立即应用，并同步保存到云端偏好设置。",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    showDivider: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = BrandPrimary)
        )
        Text(
            text = label,
            fontSize = 16.sp,
            color = TextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
    }
    if (showDivider) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp),
            color = com.geoagent.ui.theme.DividerLine
        )
    }
}
