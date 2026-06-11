# GeoScientist

<div align="center">

**通用智能助手 Android 客户端** · 基于 Kotlin + Android Views · SSE 流式对话 · 本地知识库 RAG · 多 Agent 协作

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/API-33+-3DDC84?logo=android)](https://developer.android.com)
[![Gradle](https://img.shields.io/badge/Gradle-9.3-02303A?logo=gradle)](https://gradle.org)

</div>

---

## 技术栈

| 层 | 技术 | 说明 |
|---|------|------|
| 语言 | **Kotlin** 2.0.0 | 全项目 Kotlin，协程 + Flow |
| UI | **Android Views** (XML) + Material Components 1.12 | 传统视图体系，ComposeView 仅用于搜索来源卡片 |
| 架构 | **MVVM** + Repository 模式 | 单一 `:app` 模块，清晰分层 |
| DI | **Hilt** 2.59.2 | 编译时依赖注入 |
| 网络 | **Retrofit** 2.11 + **OkHttp** 4.12 | HTTP 客户端 + SSE 长连接 |
| 流式 | Kotlin **Flow** | 对话流式输出，40ms 帧间步进渲染 |
| 搜索 | **Tavily** Search API | 联网搜索增强 |
| 向量 | **SiliconFlow** BGE-M3 | 文档嵌入与语义检索 |
| 本地存储 | **DataStore** Preferences + **SQLite** | Token / 偏好 / 对话记录 / 文档索引 |
| Markdown | **Markwon** 4.6 | 流式实时渲染 |
| 邮件 | **JavaMail** (javax.mail) | QQ SMTP 邮件发送 |

## 架构概览

```
┌──────────────────────────────────────────────┐
│                   UI Layer                    │
│  Splash → Auth → Chat → Documents → Settings │
│         (AppCompatActivity + XML)            │
├──────────────────────────────────────────────┤
│                ViewModel Layer               │
│  ChatViewModel · SearchViewModel ·           │
│  ChatManager (StateFlow + Stream Renderer)   │
├──────────────────────────────────────────────┤
│              Repository Layer                │
│  Auth · Chat · Document · Search · Settings  │
├──────────────────────────────────────────────┤
│                Data Layer                    │
│  ┌──────────┐ ┌──────────┐ ┌─────────────┐  │
│  │ Remote   │ │ Local    │ │ Agent       │  │
│  │ DeepSeek │ │ SQLite   │ │ V2 Runtime  │  │
│  │ Tavily   │ │ DataStore│ │ Orchestrator│  │
│  │ SiliFlow │ │ Chunks   │ │ IntentRouter│  │
│  └──────────┘ └──────────┘ └─────────────┘  │
└──────────────────────────────────────────────┘
```

### 数据流

用户消息 → `ChatManager.sendMessage()` → `IntentRouter.route()` 路由判断 → **本地 Agent** 或 **远端 SSE API** → `DeepSeekChatClient.streamChat()` → `ChatEvent` Flow → `ChatManager` 字符步进渲染 → `ChatMessageAdapter` Markdown 实时渲染

### SSE 流式对话

客户端通过 OkHttp 直接发起 SSE 长连接，使用 OpenAI-compatible 协议（支持 DashScope / DeepSeek / SiliconFlow 等任意兼容 API）：

```kotlin
// DeepSeekChatClient.streamChat()
val bodyMap = mapOf(
    "model" to model,
    "messages" to messages,
    "stream" to true,
    "max_tokens" to 16384,
    "enable_thinking" to enableThinking
)
```

SSE 事件通过 `Flow<ChatEvent>` 发射，`ChatManager` 以 65ms 帧间隔 + 4 字符/帧的**自适应步进策略**渲染（缓冲区积压时自动加速），确保阅读节奏平滑。Markdown 在流式过程中通过 Markwon 实时格式化（60ms 节流），用户看到的是渲染后的富文本而非原始语法。

### 思考模式优化
- **Prompt 引导简洁思考** — 系统提示词要求思考聚焦在几句话内，直接提炼关键信息
- **无硬性 token 截断** — 不设 `thinking_budget`，模型自然收尾，不会半截卡断
- **3 秒空闲超时** — 思考结束后若回答迟迟未开始，自动过渡显示"正在整理回答…"

### V2 Agent 运行时

内置多 Agent 协作框架（`V2RuntimeOrchestrator`），深度融合 One LLM：

| Agent | 职责 |
|---|---|
| **Search Agent** | 基于 Tavily 搜索证据回答，保留来源链接 |
| **RAG Agent** | 基于本地知识库文档片段回答 |
| **Research Agent** | 综合联网资料和本地文档进行研究分析 |
| **Task Agent** | 拆解用户请求为可执行待办 |
| **Schedule Agent** | 安排时间块计划，生成日历事件 |
| **Email Agent** | 邮件内容生成与 SMTP 发送 |
| **PDF Agent** | 解析 PDF 文本，提取重点和结构 |

---

## 功能一览

### 智能对话
- **智能模式** — 通用对话，支持深度思考 + 联网搜索
- **知识库模式** — 基于上传文档的 RAG 精准问答
- **思考模式** — 展示 AI 推理过程，可展开/折叠
- **追问建议** — 对话结束后自动生成 3 条追问
- **图片分析** — 支持图片 + 文字混合输入（Base64 传输）

### 流式渲染
- 65ms 帧间隔打字机动画
- Markdown 实时渲染（粗体、标题、代码块、列表等）
- 思考内容独立展开区，带耗时计时器

### 知识库
- 支持 **PDF / Word / TXT** 文档上传
- 自动分段 + 向量嵌入 + 语义检索
- 文档重命名 / 删除 / 详情查看

### 个人设置
- **账号与安全** — 修改密码、自定义头像、切换账号
- **外观** — 浅色 / 深色 / 跟随系统，跨主题动画过渡
- **自定义指令** — 设置 AI 回答风格偏好
- **API 密钥管理** — 支持自定义 API Key
- **隐私** — 导出数据、隐身模式、记忆管理

### 账户系统
- 邮箱 + 验证码注册 / 登录
- 忘记密码重置
- Token 本地持久化，自动登录

---

## 快速开始

### 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 17+ |
| Android Studio | Hedgehog (2023.1) 或更新 |
| Android SDK | API 33+ |
| Gradle | 9.3.1（项目自带 wrapper） |

### 编译运行

```bash
# 克隆项目
git clone git@github.com:Jerry0907909/GeoAgent-APP.git
cd GeoAgent-APP

# 编译安装到模拟器/真机
./gradlew installDebug

# 模拟器端口转发（如需访问本机后端）
adb reverse tcp:8000 tcp:8000
```

### 运行测试

```bash
./gradlew test                      # JVM 单元测试
./gradlew connectedAndroidTest      # 仪表化测试（需设备）
```

---

## 自定义配置

### .env 环境变量

项目根目录 `.env` 文件控制所有 API 和服务配置：

```bash
# LLM API（OpenAI-compatible，支持 DashScope / DeepSeek / SiliconFlow）
LLM_API_KEY=your-api-key
LLM_BASE_URL=https://api.example.com/v1
LLM_MODEL=your-model-name
LLM_MAX_TOKENS=16384

# SiliconFlow Embedding API
SILICONFLOW_API_KEY=your-key
SILICONFLOW_EMBED_MODEL=BAAI/bge-m3

# Tavily 联网搜索 API
TAVILY_API_KEY=your-key

# SMTP 邮件服务（QQ邮箱示例）
SMTP_HOST=smtp.qq.com
SMTP_PORT=465
SMTP_USER=your-email@qq.com
SMTP_PASSWORD=your-smtp-password
EMAIL_FROM=your-email@qq.com
```

### 切换 LLM 后端

GeoScientist 使用 OpenAI-compatible 协议，支持任意兼容 API：

| 提供商 | LLM_BASE_URL | LLM_MODEL 示例 |
|--------|-------------|---------------|
| **阿里云 DashScope** | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen3.7-plus` |
| **DeepSeek** | `https://api.deepseek.com/v1` | `deepseek-chat` |
| **SiliconFlow** | `https://api.siliconflow.cn/v1` | `Qwen/Qwen3.5-9B` |

修改 `.env` 中 `LLM_BASE_URL`、`LLM_MODEL`、`LLM_API_KEY` 即可。

### 调整流式输出速度

编辑 `ChatManager.kt` 中的常量：

```kotlin
private const val STREAM_FRAME_DELAY_MILLIS = 65L   // 帧间隔（毫秒）
private const val MAX_CHARS_PER_FRAME = 4            // 每帧字符数
```

### 自定义 AI 人设

在应用内 **设置 → 自定义指令** 中输入偏好，例如：

> 回答保持简洁，优先给出结论再展开说明。使用中文输出，避免英文术语。

该指令会自动注入到每次对话的 System Prompt 中。

---

## 项目结构

```
app/src/main/java/com/geoagent/
├── GeoAgentApp.kt                  # Application 入口
├── MainActivity.kt                 # 主容器（Navigation Drawer）
├── agent/
│   ├── AgentRegistry.kt            # IntentRouter + Email Agent（唯一意图路由 Agent）
│   └── v2/                         # V2 Agent 运行时
│       ├── V2AgentSystem.kt        # Agent 注册与元数据
│       ├── V2Runtime.kt            # Agent 执行器（Search/RAG/Research/Task/Schedule/Email/PDF）
│       └── V2ProductionRuntimeGateway.kt  # 生产环境 Gateway 实现
├── di/                             # Hilt 模块
│   ├── AppHiltModule.kt
│   ├── SearchHiltModule.kt
│   └── V2AgentHiltModule.kt
├── domain/
│   ├── model/Models.kt             # User, Message, Conversation, ChatMode
│   ├── repository/                 # Repository 接口（Auth/Chat/Document）
│   ├── SearchUseCase.kt            # 搜索策略与提示词工具
│   └── ConversationContextBuilder.kt
├── data/
│   ├── api/
│   │   ├── DeepSeekChatClient.kt   # OpenAI-compatible SSE 流式 + 同步客户端
│   │   ├── GeoAgentAuthApi.kt      # 认证 API
│   │   ├── SiliconFlowEmbeddingClient.kt
│   │   ├── TavilySearchClient.kt
│   │   ├── EmailSender.kt          # SMTP 邮件发送
│   │   └── dto/                    # 请求/响应 DTO
│   ├── local/
│   │   ├── TokenDataStore.kt       # JWT Token 持久化
│   │   ├── UserPrefsDataStore.kt   # 用户偏好
│   │   ├── AvatarLocalStore.kt     # 头像本地缓存
│   │   ├── DocumentStore.kt        # 文档元数据存储
│   │   ├── DocumentParser.kt       # PDF/Word/TXT 解析
│   │   ├── DocumentChunker.kt      # 文档分段
│   │   └── memory/                 # V2 记忆存储（Room）
│   └── repository/                 # Repository 实现
├── ui/
│   ├── theme/AppThemeHelper.kt     # 主题适配
│   ├── motion/                     # 动画 Tokens
│   ├── splash/                     # 启动页
│   ├── auth/                       # 登录 / 注册 / 忘记密码
│   ├── chat/                       # 核心对话页
│   │   ├── ChatActivity.kt         # 主对话 Activity
│   │   ├── ChatManager.kt          # 消息管理 + SSE 自适应步进渲染
│   │   ├── ChatMessageAdapter.kt   # 消息气泡 RecyclerView + Markdown 实时渲染
│   │   └── V2SystemActionMapper.kt # 系统动作映射
│   ├── documents/                  # 知识库管理
│   └── settings/                   # 设置页
└── model/                          # Tavily 请求/响应模型
```

---

## 许可证

MIT License — 详见 [LICENSE](LICENSE)
