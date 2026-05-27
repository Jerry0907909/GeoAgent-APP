# GeoAgent Android — UI 规范 (DeepSeek 风格适配版)

> 基于 DeepSeek Web 端设计系统（`chat.deepseek.com`）全面移植。
> 目标设备：**720px × 1280px / 4.65 寸**（约 360dp 宽，density ≈ 2.0）

## 1. 设计原则

DeepSeek 的视觉特征：
- **白底画布** + 单一品牌蓝 `#3964fe` 作为唯一强调色
- **高留白、低噪声**、专业克制的学术风格
- **药丸形控件**（Pill / Rounded Shell）主导交互语言
- **轻阴影、轻边框**、无厚重卡片
- 用户消息用淡蓝药丸气泡，AI 回复用白底文档式排版

---

## 2. 全局设计系统

### 2.1 颜色（Color Tokens）

```kotlin
object DeepSeekColors {
    // Brand
    val BrandPrimary = Color(0xFF3964fe)   // 主品牌蓝：按钮、发送、选中态、高亮
    val BrandSoft    = Color(0xFFedf3fe)   // 柔和蓝背景：toggle选中、用户气泡、hover
    val BrandBorder  = Color(0xFFb7c8fe)   // 品牌蓝边框：选中态边框、聚焦边框

    // Text
    val TextPrimary  = Color(0xFF0f1115)   // 主文字：标题、正文、图标默认
    val TextMuted    = Color(0xFF81858c)   // 次级文字：时间戳、placeholder、元信息
    val White        = Color(0xFFFFFFFF)   // 纯白

    // Surface
    val Surface      = Color(0xFFFFFFFF)   // 表面白：卡片、输入框、弹窗
    val Background   = Color(0xFFfafbfd)   // 全局背景：极淡蓝白偏色
    val BorderSubtle = Color(0x1A000000)   // 微妙边框：#000000@0.1

    // Semantic
    val Destructive  = Color(0xFFec1313)   // 破坏性：删除、登出
    val Success      = Color(0xFF00bc0c)   // 成功态
    val NeutralStrong= Color(0xFF404040)   // 深中性色
}
```

### 2.2 字体（Typography）

Android 字体栈：`Noto Sans SC`, `Roboto`, `system-ui`, `sans-serif`

| Token | 字号 | 行高 | 字重 | 用途 |
|---|---|---|---|---|
| `hero` | 22sp | 30sp | 600 | 空状态大标题（如"使用快速模式开始对话"） |
| `title` | 16sp | 24sp | 500 | 页面标题 / Modal 标题（如"系统设置"） |
| `body` | 16sp | 24sp | 400 | 正文 / 输入框文字 |
| `action` | 14sp | 22sp | 500 | 主按钮文字、高权重操作 |
| `meta` | 14sp | 22sp | 400 | 列表文字、链接按钮、标准操作 |
| `compactControl` | 13sp | 24sp | 500 | Toggle Pills（如"深度思考""智能搜索"） |
| `legal` | 12sp | 18sp | 400 | 法律文案、免责声明 |

```kotlin
val DeepSeekTypography = Typography(
    headlineLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    headlineMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, color = TextPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, color = TextPrimary),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, color = TextMuted)
)
```

### 2.3 间距（Spacing）

| Token | dp | 用途 |
|---|---|---|
| `micro` | 4 | 紧凑间距，icon 与文字间距 |
| `xs` | 6 | 小组件内部间隙 |
| `sm` | 8 | 按钮内边距、小卡片间距 |
| `md` | 12 | 标准内边距、toggle pill padding |
| `lg` | 16 | 卡片内边距、输入框 padding |
| `xl` | 24 | 页面级水平 padding、modal 内边距 |
| `xxl` | 32 | 大区块间距 |

### 2.4 圆角（Radius）

| Token | dp | 用途 |
|---|---|---|
| `tight` | 8 | 小输入框、紧凑按钮 |
| `surface` | 12 | 标准卡片、面板 |
| `toggle` | 18 | Toggle Pills（深度思考/智能搜索） |
| `shell` | 24 | 大输入框、Composer 外壳、Modal |
| `pill` | 100 | 主按钮、CTA（完全药丸形） |
| `circle` | 50% | Icon Button、头像 |

### 2.5 阴影 / Elevation（Android Material3 映射）

```kotlin
// DeepSeek 阴影极为克制，映射到 Material3：
val DeepSeekElevation = object {
    // 卡片/列表项：几乎无阴影
    val none = CardDefaults.cardElevation(defaultElevation = 0.dp)

    // Composer 外壳：轻微上浮感
    val composer = CardDefaults.cardElevation(defaultElevation = 2.dp)

    // Bottom Sheet / Modal
    val floating = ModalBottomSheetDefaults.Elevation
}
```

---

## 3. 核心组件样式

### 3.1 主按钮（Primary CTA）

```kotlin
Button(
    onClick = { /* action */ },
    modifier = Modifier
        .fillMaxWidth()
        .height(42.dp),
    shape = RoundedCornerShape(100.dp),  // pill
    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
) {
    Text("登录", color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
}
```

**规格：**
- Background: `#3964fe`
- Text: `#ffffff`, 14sp, weight 500
- Border: none
- BorderRadius: `100dp`（完全药丸）
- Sample: `325px x 42px`（桌面），手机端 fillWidth, height 42dp

### 3.2 链接按钮（Link Button）

```kotlin
TextButton(onClick = { /* action */ }) {
    Text("发送验证码", color = BrandPrimary, fontSize = 14.sp)
}
```

**规格：**
- Background: transparent
- Text: `#3964fe`, 14sp, weight 400
- Border: none

### 3.3 Toggle Pills（深度思考 / 智能搜索）

**默认态：**
- Background: transparent
- Text: `#0f1115`, 13sp, weight 500
- Border: `1dp solid #000000@0.1`
- BorderRadius: 18dp
- Padding: horizontal 12dp
- Size: ~96dp x 34dp

**选中态：**
- Background: `#edf3fe`
- Text: `#3964fe`
- Border: `1dp solid #b7c8fe`
- BorderRadius: 18dp

```kotlin
@Composable
fun DeepSeekTogglePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                if (selected) BrandSoft else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .border(
                1.dp,
                if (selected) BrandBorder else BorderSubtle,
                RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) BrandPrimary else TextPrimary
        )
    }
}
```

### 3.4 Icon Button

**默认态：**
- Background: transparent
- Color: `#81858c`（muted）或 `#0f1115`（body ink）
- OuterSize: `34dp x 34dp`
- IconSize: 14dp ~ 16dp
- BorderRadius: 50%（圆形）

**发送态：**
- Background: `#3964fe`
- Color: `#ffffff`
- OuterSize: `34dp x 34dp`
- BorderRadius: 50%

### 3.5 Composer（输入框外壳）

DeepSeek 的 Composer 是一个大圆角白底外壳，内部放置 textarea。

```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
    shape = RoundedCornerShape(24.dp),  // shell 圆角
    colors = CardDefaults.cardColors(containerColor = Surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
) {
    Column(modifier = Modifier.padding(12.dp)) {
        // Textarea
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("输入问题...", color = TextMuted, fontSize = 16.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            textStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        )
        // 底部工具行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                IconButton(onClick = onImagePick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Image, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
            // 发送按钮（圆形品牌蓝）
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier
                    .size(34.dp)
                    .background(BrandPrimary, shape = CircleShape)
            ) {
                Icon(Icons.Default.Send, null, tint = White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
```

**Composer Textarea 规格：**
- FontSize: 16sp
- LineHeight: 24sp
- Color: `#0f1115`
- Padding: `12dp 12dp 0dp 16dp`
- Border: none

---

## 4. 页面详细设计

### 4.1 登录页面 (LoginScreen)

**布局：**
```
Column (
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
        .fillMaxSize()
        .background(Background)
        .padding(horizontal = 24.dp)
) {
    // Logo
    Logo(radius = 48.dp, color = BrandPrimary)
    Spacer(height = 40.dp)

    // 邮箱输入 - DeepSeek 风格：大圆角、轻边框
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("邮箱") },
        keyboardOptions = KeyboardOptions(keyboardType = Email),
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
            focusedIndicatorColor = BrandBorder,
            unfocusedIndicatorColor = BorderSubtle
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    )
    Spacer(height = 16.dp)

    // 密码输入
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("密码") },
        visualTransformation = PasswordVisualTransformation(),
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
            focusedIndicatorColor = BrandBorder,
            unfocusedIndicatorColor = BorderSubtle
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    )
    Spacer(height = 8.dp)

    // 忘记密码 - Link 风格
    TextButton(onClick = { /* forgot password */ }) {
        Text("忘记密码?", color = BrandPrimary, fontSize = 14.sp)
    }
    Spacer(height = 24.dp)

    // 登录按钮 - DeepSeek Primary CTA
    Button(
        onClick = { login() },
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
    ) {
        Text("登录", color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
    Spacer(height = 16.dp)

    // 注册入口
    TextButton(onClick = { navigateToRegister() }) {
        Text("没有账号？立即注册", color = BrandPrimary, fontSize = 14.sp)
    }
}
```

**状态：**
- `idle` — 初始状态
- `loading` — 请求中（按钮显示 CircularProgressIndicator，品牌蓝 `#3964fe`）
- `error` — Snackbar 显示错误

---

### 4.2 聊天列表页面 (ChatListScreen)

**布局：**
```
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Geo-Agent", fontSize = 18.sp, fontWeight = FontWeight.Medium) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            actions = {
                IconButton(onClick = { navigateToSettings() }) {
                    Icon(Icons.Default.Settings, contentDescription = "设置", tint = TextMuted)
                }
            }
        )
    },
    floatingActionButton = {
        FloatingActionButton(
            onClick = { newChat() },
            shape = RoundedCornerShape(100.dp),
            containerColor = BrandPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "新对话", tint = White)
        }
    }
) { padding ->
    LazyColumn(modifier = Modifier.padding(padding)) {
        items(groupedConversations) { group ->
            Text(
                group.dateLabel,
                style = labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            group.conversations.forEach { conversation ->
                ChatListItem(conversation)
            }
        }
    }
}
```

**ChatListItem（DeepSeek 风格 - 紧凑药丸行）：**
```
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp)
        .clickable { navigateToChat(conversation.id) }
        .background(
            if (isActive) BrandSoft else Surface,
            shape = RoundedCornerShape(12.dp)
        )
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Icon(
        imageVector = Icons.Default.ChatBubble,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = if (isActive) BrandPrimary else TextMuted
    )
    Spacer(width = 10.dp)
    Column(modifier = Modifier.weight(1f)) {
        Text(
            conversation.title ?: "新对话",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp,
            color = TextPrimary
        )
        Text(
            conversation.lastMessage,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = TextMuted
        )
    }
}
```

---

### 4.3 聊天详情页面 (ChatDetailScreen)

**布局：**
```
Scaffold(
    topBar = {
        ChatTopBar(
            title = conversationTitle,
            actions = {
                // 模式切换 Toggle Pills
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DeepSeekTogglePill("对话", currentMode == Chat, { currentMode = Chat })
                    DeepSeekTogglePill("RAG", currentMode == RAG, { currentMode = RAG })
                }
            }
        )
    }
) { padding ->
    Column(modifier = Modifier.padding(padding)) {
        // 消息列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true
        ) {
            items(messages.reversed()) { message ->
                ChatMessageBubble(message)
            }
        }

        // 底部 Composer
        ChatComposer(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = { sendMessage(inputText) },
            onImagePick = { /* 选择图片 */ }
        )
    }
}
```

**ChatMessageBubble（DeepSeek 风格）：**
```kotlin
@Composable
fun ChatMessageBubble(message: Message) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        if (isUser) {
            // 用户消息：淡蓝背景小药丸气泡
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(BrandSoft, shape = RoundedCornerShape(18.dp))
                    .border(1.dp, BrandBorder.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 280.dp)
            ) {
                Text(
                    text = message.content,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = TextPrimary
                )
            }
        } else {
            // AI 消息：白底无卡片，文档式排版
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.content,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = TextPrimary
                )

                // 来源展示（仅 AI 消息）
                if (message.sources.isNotEmpty()) {
                    Spacer(height = 8.dp)
                    SourcesChipRow(message.sources)
                }

                // 操作工具行（复制、重新生成等）
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { /* copy */ },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { /* regenerate */ },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
```

---

### 4.4 文档列表页面 (DocumentListScreen)

**布局：**
```
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("知识库", fontSize = 18.sp) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
        )
    },
    floatingActionButton = {
        FloatingActionButton(
            onClick = { navigateToUpload() },
            shape = RoundedCornerShape(100.dp),
            containerColor = BrandPrimary
        ) {
            Icon(Icons.Default.Upload, null, tint = White)
        }
    }
) { padding ->
    LazyColumn(modifier = Modifier.padding(padding)) {
        items(documents) { document ->
            DocumentListItem(document)
        }
    }
}
```

**DocumentListItem：**
```
Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 文件图标
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(BrandSoft, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.InsertDriveFile, null, tint = BrandPrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(width = 12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(document.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            Text(
                "${document.size.formatSize()} · ${document.created_at}",
                fontSize = 13.sp,
                color = TextMuted
            )
        }
        // 删除按钮
        IconButton(onClick = { deleteDocument(document.id) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Delete, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
```

---

### 4.5 文档上传页面 (UploadScreen)

**布局：**
```
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("上传文档", fontSize = 18.sp) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
        )
    }
) { padding ->
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 拖拽/选择区域 - 虚线边框
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = BorderStroke(2.dp, BrandBorder.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { pickFiles() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.UploadFile, null, tint = BrandPrimary, modifier = Modifier.size(48.dp))
                Spacer(height = 12.dp)
                Text("点击上传文档", fontSize = 16.sp, color = TextPrimary)
                Spacer(height = 4.dp)
                Text("支持 PDF, Word, TXT", fontSize = 13.sp, color = TextMuted)
            }
        }

        Spacer(height = 24.dp)

        // 已选择文件列表
        selectedFiles.forEach { file ->
            SelectedFileItem(file, onRemove = { removeFile(file) })
        }

        Spacer(height = 24.dp)

        // 上传按钮 - 主 CTA 药丸
        Button(
            onClick = { uploadFiles() },
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            enabled = selectedFiles.isNotEmpty(),
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
        ) {
            Text("上传 ${selectedFiles.size} 个文件", color = White, fontSize = 16.sp)
        }
    }
}
```

---

### 4.6 设置页面 (SettingsScreen)

**布局：**
```
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("设置", fontSize = 18.sp) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
        )
    }
) { padding ->
    LazyColumn(modifier = Modifier.padding(padding)) {
        // 用户卡片
        item { UserProfileCard(user) }

        // 偏好设置
        item { SettingsSection("偏好") }
        item { ThemeToggleItem(currentTheme, onChange = { theme = it }) }
        item { LanguageSelector(currentLanguage, onChange = { language = it }) }

        // 账户
        item { SettingsSection("账户") }
        item {
            SettingsItem(
                icon = Icons.Default.Lock,
                title = "修改密码",
                onClick = { navigateToChangePassword() }
            )
        }
        item {
            // 登出 - 破坏性操作，红色 outline 按钮（DeepSeek Settings Danger Button）
            OutlinedButton(
                onClick = { logout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(40.dp),
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, Destructive),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Destructive)
            ) {
                Text("退出登录", color = Destructive, fontSize = 14.sp)
            }
        }

        // 关于
        item { SettingsSection("关于") }
        item { SettingsItem(title = "版本", subtitle = "v1.0.0", clickable = false) }
    }
}
```

---

## 5. 状态管理

### 5.1 ChatViewModel 状态机

```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val documentRepository: DocumentRepository
) : ViewModel() {

    // UI 状态
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    // SSE 流
    private var sseJob: Job? = null

    fun sendMessage(text: String, mode: ChatMode = ChatMode.CHAT) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            sseJob?.cancel()
            sseJob = viewModelScope.launch {
                chatRepository.streamChat(text, mode)
                    .catch { error ->
                        _uiState.update { it.copy(error = error.message, isLoading = false) }
                    }
                    .collect { event ->
                        when (event) {
                            is ChatEvent.Content -> {
                                _uiState.update { state ->
                                    val lastMessage = state.messages.lastOrNull()
                                    if (lastMessage?.role == "assistant") {
                                        val updated = lastMessage.copy(
                                            content = lastMessage.content + event.content
                                        )
                                        state.copy(messages = state.messages.dropLast(1) + updated)
                                    } else {
                                        state.copy(messages = state.messages + Message.assistant(event.content))
                                    }
                                }
                            }
                            is ChatEvent.Sources -> {
                                // 更新来源信息
                            }
                            is ChatEvent.Done -> {
                                _uiState.update { it.copy(isLoading = false) }
                            }
                        }
                    }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sseJob?.cancel()
    }
}
```

---

## 6. 导航设计

### 6.1 导航图

```
+-- SplashScreen --+
                   |
       +-----------+-----------+
       |                       |
   LoginScreen            RegisterScreen
       |
       +---> MainScreen (BottomNavigation)
                   |
         +--------+--------+--------+
         |                 |         |
    ChatListScreen    DocListScreen SettingsScreen
         |                 |
    ChatDetailScreen  UploadScreen
```

### 6.2 Bottom Navigation（DeepSeek 风格）

| 图标 | 标签 | 目标 |
|---|---|---|
| ChatBubble | 对话 | ChatListScreen |
| LibraryBooks | 知识库 | DocumentListScreen |
| Settings | 设置 | SettingsScreen |

```kotlin
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = Surface,
        tonalElevation = 0.dp
    ) {
        val items = listOf(
            Triple("chat", "对话", Icons.Default.ChatBubble),
            Triple("documents", "知识库", Icons.Default.LibraryBooks),
            Triple("settings", "设置", Icons.Default.Settings)
        )
        items.forEach { (route, label, icon) ->
            val selected = currentRoute == route
            NavigationBarItem(
                icon = { Icon(icon, null, tint = if (selected) BrandPrimary else TextMuted) },
                label = { Text(label, fontSize = 12.sp) },
                selected = selected,
                onClick = { onNavigate(route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandPrimary,
                    selectedTextColor = BrandPrimary,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
    }
}
```

---

## 7. 交互规范

### 7.1 手势操作

| 手势 | 触发区域 | 动作 |
|---|---|---|
| 长按对话项 | ChatListScreen | 显示删除确认弹窗 |
| 下拉刷新 | ChatListScreen / DocListScreen | 刷新列表数据 |
| 左滑删除 | ChatListItem | 快速删除对话 |
| 点击图片 | ChatMessageBubble | 全屏预览 |
| 下拉输入区 | ChatDetailScreen | 收起键盘 |

### 7.2 动画规范

| 场景 | 动画 | 时长 |
|---|---|---|
| 页面进入 | fadeIn + slideUp | 300ms, easeOut |
| 页面退出 | fadeOut + slideDown | 200ms, easeIn |
| 消息发送 | scale(0.9 → 1.0) + fadeIn | 200ms |
| 流式文字 | 逐字符 appear | 无 |
| 加载状态 | CircularProgressIndicator（品牌蓝 `#3964fe`） | 持续 |
| 错误提示 | Snackbar slideUp | 300ms, 停留 3s |

---

## 8. 响应式设计（720px × 1280px 适配）

720px 宽度在 Android 中约等于 `360dp`（density ≈ 2.0），属于标准手机宽度。

### 8.1 尺寸换算表

| DeepSeek 桌面值 | Android 适配值 | 说明 |
|---|---|---|
| 480px 用户气泡 | 280dp maxWidth | 按比例缩小，保持右侧对齐 |
| 776px Composer | fillMaxWidth (360dp) | 全宽适配 |
| 96px × 34px Toggle | 96dp × 34dp | 保持原尺寸 |
| 325px × 42px 主按钮 | fillMaxWidth × 42dp | 全宽药丸 |
| 34px Icon Button | 34dp | 触摸友好，保持原尺寸 |
| 261px Sidebar | 隐藏 / Drawer | 手机端用 Drawer 替代 |

### 8.2 屏幕适配

| 断点 | 布局调整 |
|---|---|
| < 360dp | 紧凑间距，减小字体 |
| 360-600dp | 标准布局（当前目标 720px） |
| > 600dp | 双栏布局（左侧列表，右侧详情） |

### 8.3 横屏支持

聊天页面横屏时，左侧显示对话列表（占 40%），右侧显示聊天详情（占 60%）。

---

## 附录：DeepSeek 原始设计数据引用

| 属性 | 原始值 | 文件来源 |
|---|---|---|
| Brand Primary | `#3964fe` | `design-tokens.json` → palette.brandPrimary |
| Brand Soft | `#edf3fe` | `design-tokens.json` → palette.brandSoft |
| Brand Border | `#b7c8fe` | `design-tokens.json` → palette.brandBorder |
| Text Primary | `#0f1115` | `design-tokens.json` → palette.textPrimary |
| Text Muted | `#81858c` | `design-tokens.json` → palette.textMuted |
| Border Subtle | `#000000@0.1` | `design-tokens.json` → palette.borderSubtle |
| Destructive | `#ec1313` | `design-tokens.json` → palette.destructive |
| Success | `#00bc0c` | `design-tokens.json` → palette.successAccent |
| Hero Title | 24px/32px/600 | `design-tokens.json` → typography.scale.hero |
| Title | 16px/24px/500 | `design-tokens.json` → typography.scale.title |
| Action | 14px/22px/500 | `design-tokens.json` → typography.scale.action |
| Compact Control | 13px/24px/500 | `design-tokens.json` → typography.scale.compactControl |
| Legal | 12px/18px/400 | `design-tokens.json` → typography.scale.legal |
| Primary Button Size | 325px × 42px | `design-tokens.json` → components.primaryButton |
| Toggle Pill Size | 96px × 34px | `design-tokens.json` → components.togglePillDefault |
| Icon Button Outer | 34px × 34px | `design-tokens.json` → components.iconButton |
| Composer Padding | 12px 12px 0px 16px | `design-tokens.json` → components.composerTextarea |
| Toggle BorderRadius | 18px | `design-tokens.json` → radius.toggle |
| Shell BorderRadius | 24px | `design-tokens.json` → radius.shell |
| Pill BorderRadius | 100px | `design-tokens.json` → radius.pill |

---

*基于 DeepSeek Web (`chat.deepseek.com`) 设计系统提取数据适配。*
*数据来源：`deepseek-style/references/design-tokens.json`, `deepseek-style/references/DESIGN.md`, `deepseek-ui-distill/*.json`*
*Last updated: 2026-05-25.*
