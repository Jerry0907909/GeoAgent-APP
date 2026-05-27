# GeoAgent Android — 技术规范

## 1. 架构决策

### 1.1 Clean Architecture 分层

```
Presentation → ViewModel → UseCase → Repository → DataSource
     ↑                                                     ↑
  Compose Screen                                   API / Local DB
```

| 层 | 职责 | 依赖方向 |
|---|---|---|
| **Presentation** | Compose UI、状态观察、用户事件处理 | 仅依赖 ViewModel |
| **ViewModel** | 管理 UI 状态、调用 UseCase | 仅依赖 UseCase |
| **UseCase** | 单一业务逻辑单元，组合 Repository 操作 | 仅依赖 Repository Interface |
| **Repository** | 业务数据逻辑，协调 API + Local | 仅依赖 DataSource |
| **DataSource** | API Service 或 Local DB 原始操作 | 无外部依赖 |

### 1.2 依赖注入（Hilt）

```kotlin
// NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(tokenDataStore: TokenDataStore): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenDataStore))
            .authenticator(TokenAuthenticator(tokenDataStore))
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8000/api/") // Emulator localhost
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
```

> **注意**: `10.0.2.2` 是 Android Emulator 访问宿主机 localhost 的特殊地址。开发时通过 `adb reverse tcp:8000 tcp:8000` 映射后，也可直接用 `http://localhost:8000/api/`。

### 1.3 后端 `.env` 配置检查

Android APP 本身不直接调用外部服务，但后端依赖多个外部 API。在启动后端前，需要确保 `.env` 文件已正确配置：

| 服务 | `.env` 变量 | 说明 | 获取方式 |
|---|---|---|---|
| **SiliconFlow LLM** | `API_KEY` | 统一 API Key（LLM + Embedding + Rerank 共用） | https://siliconflow.cn |
| **SiliconFlow LLM** | `LLM_MODEL_NAME` | 当前配置：`Pro/moonshotai/Kimi-K2.6` | 控制台查看 |
| **SiliconFlow Embedding** | `EMBEDDING_MODEL_NAME` | `BAAI/bge-m3` | 同上 |
| **Tavily Search** | `TAVILY_API_KEY` | Web 搜索 API Key | https://tavily.com |
| **QQ SMTP** | `SMTP_USER`, `SMTP_PASSWORD` | 邮箱验证码发送 | QQ 邮箱 → 账户安全 → 授权码 |
| **MySQL** | `MYSQL_*` | 用户/会话数据存储 | 本地 MySQL |
| **JWT** | `JWT_SECRET_KEY` | 签名密钥（生产环境务必修改） | 手动生成 |

**`.env` 快速验证脚本：**

```bash
#!/bin/bash
# check_env.sh
required_vars=("API_KEY" "LLM_MODEL_NAME" "TAVILY_API_KEY" "SMTP_USER" "SMTP_PASSWORD" "MYSQL_PASSWORD")
for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "❌ 缺少环境变量: $var"
    else
        echo "✅ $var 已配置"
    fi
done
```

## 2. 模块结构

### 2.1 Gradle 模块划分

```kotlin
// settings.gradle.kts
include(":app")
```

单模块 APP 足够。如果后续需要拆分为多模块（如 `:domain` + `:data`），可再拆分。

### 2.2 包命名规范

```
com.geoagent.{feature}.{layer}
```

示例:
- `com.geoagent.chat.ui` — Chat 的 Compose UI
- `com.geoagent.chat.data.repository` — Chat 的 Repository 实现
- `com.geoagent.chat.domain.model` — Chat 的实体/模型

## 3. 数据流

### 3.1 通用数据流模式

```
UserAction → ViewModel → UseCase → Repository → API/DB
                                              ↓
UI Update ← StateFlow ← ViewModel ← UseCase ← Result
```

### 3.2 Chat SSE 流式数据流

```
UserSend → ChatViewModel
           ↓
      Launch SSE Connection (OkHttp EventSource)
           ↓
      Flow<ChatEvent> ← 每收到一个 SSE event
           ↓
      ChatUiState.messages.add(event.content)
           ↓
      Compose UI recomposes with new messages
```

## 4. 核心组件

### 4.1 SSE Client

```kotlin
class ChatSseClient(
    private val client: OkHttpClient,
    private val baseUrl: String
) {
    fun streamChat(request: ChatStreamRequest): Flow<ChatEvent> = callbackFlow {
        val eventSource = EventSource.Builder(
            object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    // Connection opened
                }

                override fun onMessage(
                    eventSource: EventSource,
                    id: String?, type: String?, data: String
                ) {
                    // Parse JSON data → ChatEvent
                    val event = parseChatEvent(data)
                    trySend(event)
                }

                override fun onFailure(
                    eventSource: EventSource, t: Throwable?, response: Response?
                ) {
                    close(t) // Propagate error
                }
            }
        ).build()

        eventSource.connect()

        awaitClose { eventSource.close() }
    }
}
```

### 4.2 AuthInterceptor

```kotlin
class AuthInterceptor(private val tokenDataStore: TokenDataStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenDataStore.accessToken.first() }
        val request = chain.request().newBuilder()
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return chain.proceed(request)
    }
}
```

### 4.3 TokenAuthenticator

```kotlin
class TokenAuthenticator(private val tokenDataStore: TokenDataStore) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("X-Retry-With-Refresh") != null) return null

        val refreshToken = runBlocking { tokenDataStore.refreshToken.first() } ?: return null

        // Refresh token
        val newToken = runBlocking { refreshAccessToken(refreshToken) } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .header("X-Retry-With-Refresh", "true")
            .build()
    }
}
```

## 5. 本地存储

### 5.1 DataStore（Token + 偏好）

```kotlin
class TokenDataStore(context: Context) {
    private val dataStore = context.dataStore

    val accessToken: Flow<String?> = dataStore.data
        .map { it[ACCESS_TOKEN_KEY] }

    suspend fun saveTokens(access: String, refresh: String) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = access
            prefs[REFRESH_TOKEN_KEY] = refresh
        }
    }
}
```

### 5.2 Room（对话缓存）

```kotlin
@Entity
data class CachedConversation(
    @PrimaryKey val id: Int,
    val title: String,
    val lastMessage: String,
    val updatedAt: String
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM cached_conversation ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<CachedConversation>>
}
```

## 6. 主题设计（DeepSeek 风格）

### 6.1 Material3 Theme

基于 DeepSeek Web 设计系统（`chat.deepseek.com`）提取的颜色和排版。

```kotlin
// GeoAgentTheme.kt
@Composable
fun GeoAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF5a7fff),      // DeepSeek brandPrimary 提亮
            onPrimary = Color(0xFF0f1115),
            primaryContainer = Color(0xFF1a2b4d), // DeepSeek brandSoft dark
            surface = Color(0xFF1a1a1a),
            background = Color(0xFF0f0f0f),
            onSurface = Color(0xFFf0f0f0),
            outline = Color(0xFF2a2a2a),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3964fe),      // DeepSeek brandPrimary
            onPrimary = Color(0xFFffffff),
            primaryContainer = Color(0xFFedf3fe), // DeepSeek brandSoft
            surface = Color(0xFFffffff),
            background = Color(0xFFfafbfd),
            onSurface = Color(0xFF0f1115),
            outline = Color(0x1A000000),      // DeepSeek borderSubtle
        )
    }

    MaterialTheme(colorScheme = colorScheme, typography = GeoAgentTypography) {
        content()
    }
}
```

## 7. 依赖版本

```kotlin
// build.gradle.kts (app level)
dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.launchdarkly:okhttp-eventsource:4.1.1")

    // Local Storage
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Image
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```
