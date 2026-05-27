package com.geoagent.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.ui.components.CorporateConfirmDialog
import com.geoagent.ui.components.SettingsCard
import com.geoagent.ui.components.SettingsNavRow
import com.geoagent.ui.components.SettingsSectionTitle
import com.geoagent.ui.components.UserAvatar
import com.geoagent.ui.theme.Background
import com.geoagent.ui.theme.BrandBorder
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.CardSurface
import com.geoagent.ui.theme.Surface
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(
    onBack: () -> Unit,
    onSwitchAccount: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showSwitchConfirm by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    var nameInput by remember(state.user?.full_name) {
        mutableStateOf(state.user?.full_name ?: state.user?.username.orEmpty())
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setLocalAvatarFromPicker(it.toString()) }
    }

    LaunchedEffect(Unit) { viewModel.loadUser() }
    LaunchedEffect(state.switchAccountRequested) {
        if (state.switchAccountRequested) onSwitchAccount()
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }
    LaunchedEffect(state.passwordChangeSuccess) {
        if (state.passwordChangeSuccess) {
            showPasswordDialog = false
            viewModel.clearPasswordChangeSuccess()
        }
    }

    if (showSwitchConfirm) {
        CorporateConfirmDialog(
            title = "切换账号",
            message = "将退出当前账号并返回登录页，可使用其他账号登录。",
            confirmText = "切换",
            onConfirm = {
                showSwitchConfirm = false
                viewModel.requestSwitchAccount()
            },
            onDismiss = { showSwitchConfirm = false }
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            serverError = state.passwordError,
            onDismiss = {
                showPasswordDialog = false
                viewModel.clearPasswordError()
            },
            onConfirm = { current, newPwd, confirm ->
                viewModel.changePassword(current, newPwd, confirm)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("账号与安全", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextPrimary) },
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
            item { SettingsSectionTitle("账号管理") }
            item {
                SettingsCard {
                    state.user?.let { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                displayName = user.full_name ?: user.username,
                                remoteUrl = user.avatar_url,
                                localUri = state.localAvatarUri,
                                sizeDp = 52
                            )
                            Spacer(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    user.full_name ?: user.username,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(user.email, fontSize = 13.sp, color = TextMuted)
                                Text("ID: ${user.id}", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    }
                    SettingsNavRow(
                        icon = Icons.Filled.SwapHoriz,
                        title = "切换账号",
                        showDivider = false,
                        onClick = { showSwitchConfirm = true }
                    )
                }
            }

            item { SettingsSectionTitle("个人资料") }
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("头像", fontSize = 14.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            UserAvatar(
                                displayName = state.user?.full_name ?: state.user?.username,
                                remoteUrl = state.user?.avatar_url,
                                localUri = state.localAvatarUri,
                                sizeDp = 72
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { pickImage.launch("image/*") },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = BrandPrimary
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text("从相册选择照片", color = BrandPrimary)
                                }
                                Text(
                                    "支持 JPG、PNG 等常见图片格式",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("昵称", fontSize = 14.sp, color = TextMuted)
                            TextButton(onClick = { editingName = !editingName }) {
                                Text(if (editingName) "取消" else "编辑", color = BrandPrimary)
                            }
                        }
                        if (editingName) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandPrimary,
                                    unfocusedBorderColor = BrandBorder
                                )
                            )
                            TextButton(
                                onClick = {
                                    viewModel.updateDisplayName(nameInput)
                                    editingName = false
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("保存", color = BrandPrimary, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            Text(
                                state.user?.full_name ?: state.user?.username.orEmpty(),
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            item { SettingsSectionTitle("安全管理") }
            item {
                SettingsCard {
                    SettingsNavRow(
                        icon = Icons.Filled.Lock,
                        title = "修改密码",
                        showDivider = false,
                        onClick = { showPasswordDialog = true }
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    serverError: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(16.dp),
            color = CardSurface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("修改密码", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                PasswordField("当前密码", current) { current = it }
                PasswordField("新密码", newPwd) { newPwd = it }
                PasswordField("确认新密码", confirmPwd) { confirmPwd = it }
                (localError ?: serverError)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
                    TextButton(onClick = {
                        when {
                            current.isBlank() -> localError = "请输入当前密码"
                            newPwd.length < 6 -> localError = "新密码至少 6 位"
                            newPwd != confirmPwd -> localError = "两次输入的新密码不一致"
                            else -> {
                                localError = null
                                onConfirm(current, newPwd, confirmPwd)
                            }
                        }
                    }) {
                        Text("保存", color = BrandPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandPrimary,
            unfocusedBorderColor = BrandBorder
        )
    )
}
