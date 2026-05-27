package com.geoagent.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.geoagent.navigation.Routes
import com.geoagent.ui.theme.BrandBorder
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.BorderSubtle
import com.geoagent.ui.theme.Surface
import com.geoagent.ui.theme.TextMuted
import com.geoagent.ui.theme.TextPrimary
import com.geoagent.ui.theme.White
import kotlinx.coroutines.delay
import org.koin.androidx.compose.getViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    navController: NavHostController,
    viewModel: AuthViewModel = getViewModel()
) {
    val usernameRegex = remember { Regex("^[a-zA-Z0-9_]{3,50}$") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    val state by viewModel.state.collectAsState()
    val codeSent by viewModel.codeSent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state) {
        when (val s = state) {
            is AuthState.Success -> {
                navController.navigate(Routes.MAIN) { popUpTo(Routes.SPLASH) { inclusive = true } }
            }
            is AuthState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearError()
            }
            else -> {}
        }
    }

    LaunchedEffect(codeSent) {
        if (codeSent) {
            countdown = 60
            while (countdown > 0) {
                delay(1000); countdown--
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("注册", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(username, { username = it }, label = { Text("用户名") }, singleLine = true, shape = RoundedCornerShape(24.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            if (username.isNotBlank() && !usernameRegex.matches(username.trim())) {
                Text(
                    text = "用户名需 3-50 位，仅支持字母/数字/下划线",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(email, { email = it }, label = { Text("邮箱") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, shape = RoundedCornerShape(24.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(verificationCode, { verificationCode = it }, label = { Text("验证码") }, singleLine = true, shape = RoundedCornerShape(24.dp), colors = fieldColors(), modifier = Modifier.weight(1f))
                TextButton(onClick = { if (countdown == 0 && email.isNotBlank()) viewModel.sendVerificationCode(email) },
                    enabled = countdown == 0 && email.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (countdown > 0) "${countdown}s" else "发送验证码", color = if (countdown > 0) TextMuted else BrandPrimary, fontSize = 14.sp)
                }
            }
            if (verificationCode.isNotBlank() && !verificationCode.trim().matches(Regex("^\\d{6}$"))) {
                Text(
                    text = "验证码必须是 6 位数字",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(password, { password = it }, label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, shape = RoundedCornerShape(24.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(confirmPassword, { confirmPassword = it }, label = { Text("确认密码") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, shape = RoundedCornerShape(24.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.register(
                        username.trim(),
                        email.trim(),
                        password,
                        verificationCode.trim()
                    )
                },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                enabled = usernameRegex.matches(username.trim()) &&
                    email.isNotBlank() &&
                    password.isNotBlank() &&
                    confirmPassword.isNotBlank() &&
                    verificationCode.trim().matches(Regex("^\\d{6}$")) &&
                    state !is AuthState.Loading &&
                    password == confirmPassword,
                shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                if (state is AuthState.Loading) CircularProgressIndicator(color = White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("注册", color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            TextButton(onClick = { navController.popBackStack() }) { Text("已有账号？去登录", color = BrandPrimary, fontSize = 14.sp) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Surface, unfocusedContainerColor = Surface,
    focusedBorderColor = BrandBorder, unfocusedBorderColor = BorderSubtle
)