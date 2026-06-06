# GeoAgent Android

AI 地质文献智能问答系统 Android 客户端（Android Views + Material Components）。

## 环境要求

| 工具 | 最低版本 | 说明 |
|------|----------|------|
| **Android Studio** | Hedgehog (2023.1) 或更新 | 推荐，自动处理 SDK 和模拟器 |
| **JDK** | 17+ | 编译必需 |
| **Gradle** | 9.3.1 | 项目自带 wrapper，无需手动安装 |
| **Android SDK** | API 33+（Android 13+） | minSdk=33, targetSdk=36 |

## 后端配置

默认后端 Base URL：

`http://10.0.2.2:8000/api/`

> `10.0.2.2` 是 Android 模拟器访问宿主机 `localhost` 的别名。

如需让模拟器访问宿主机的后端，可执行：

```bash
adb reverse tcp:8000 tcp:8000
```

## 快速启动（零配置）

```bash
# 1. 进入项目目录
cd GeoAgent_APP

# 2. 编译并安装到模拟器/真机
./gradlew installDebug

# 3. 打开 APP
```

APP 启动后包含：

1. **启动页**（2 秒品牌展示）
2. **登录 / 注册**
3. **对话页** — SSE 流式回复
4. **知识库** — 文档列表、上传、删除确认弹窗
5. **设置** — 账号与安全（头像、昵称、改密、切换账号）、外观（浅色/深色/跟随系统）

> **已登录状态保持**：登录后 token 持久化存储，下次打开 APP 自动跳过登录页进入主页。

## 运行测试

```bash
# 单元测试（JVM，无需设备）
./gradlew test

# 仪表化测试（需要模拟器/真机）
./gradlew connectedAndroidTest
```

（如需补充测试覆盖范围，可在后续迭代完善）

## 项目结构

```
app/src/main/java/com/geoagent/
├── GeoAgentApp.kt                # Application 入口，启动 Koin + 内嵌服务器
├── agent/                        # 本地智能代理系统
│   ├── AgentRegistry.kt          # IntentRouter + 内置 Agent 定义（6 个）
│   └── UnitConversionAgent.kt    # 地质单位换算（深度/压力/温度/角度）
├── di/                           # Koin 依赖注入模块（4 个）
│   ├── NetworkModule.kt          # OkHttp + Retrofit + HTTP 缓存
│   ├── RepositoryModule.kt       # Repository 接口→实现绑定
│   ├── DataStoreModule.kt        # TokenDataStore / UserPrefsDataStore / AvatarLocalStore
│   └── DatabaseModule.kt         # 空占位（Room 未集成）
├── domain/
│   ├── model/Models.kt           # User, Message, Conversation, UserPreferences
│   └── repository/               # Repository 接口（Auth/Chat/Document/Search）
├── data/
│   ├── api/                      # Retrofit 接口（GeoAgentApi.kt）
│   │   ├── dto/                  # 请求/响应 DTO（Auth/Chat/Conversation/Document/Search）
│   │   ├── AuthInterceptor.kt    # 请求拦截器（自动附加 Token）
│   │   ├── TokenAuthenticator.kt # 401 自动刷新 Token
│   │   ├── SseClient.kt          # 对话 SSE 流式客户端
│   │   ├── SearchSseClient.kt    # 搜索 SSE 流式客户端
│   │   └── MockInterceptor.kt    # 本地 Mock 拦截器
│   ├── local/                    # DataStore Preferences 持久化
│   │   ├── TokenDataStore.kt     # JWT Token
│   │   ├── UserPrefsDataStore.kt # 用户偏好（主题等）
│   │   └── AvatarLocalStore.kt   # 头像本地缓存
│   └── repository/               # Repository 实现（Auth/Chat/Document/Search）
├── server/
│   └── GeoAgentServer.kt         # NanoHTTPD 内嵌服务器（默认关闭）
└── ui/                           # 界面层（全部 AppCompatActivity）
    ├── theme/
    │   └── AppThemeHelper.kt     # 主题适配（浅色/深色/跟随系统）
    ├── splash/
    │   └── SplashActivity.kt     # 启动页（token 检查 + 自动跳转）
    ├── auth/                     # 登录/注册/忘记密码
    │   ├── LoginActivity.kt
    │   ├── RegisterActivity.kt
    │   └── ForgotPasswordActivity.kt
    ├── chat/                     # 核心对话页（SSE 流式 + 图片 + 模式切换）
    │   ├── ChatActivity.kt       # 主对话 Activity（DrawerLayout 侧边栏）
    │   ├── ChatManager.kt        # 消息发送/SSE 接收/本地 Agent 路由
    │   ├── ChatMessageAdapter.kt # 消息气泡 RecyclerView 适配器
    │   ├── ConversationAdapter.kt# 会话列表适配器
    │   └── DeepSeekModeSwitch.kt # CHAT/RAG 模式切换控件
    ├── documents/                # 知识库管理
    │   ├── DocumentListActivity.kt
    │   ├── DocumentDetailActivity.kt
    │   └── DocumentAdapter.kt
    └── settings/                 # 设置页
        ├── SettingsActivity.kt   # 设置主页
        ├── AccountSecurityActivity.kt  # 账号与安全
        └── AppearanceActivity.kt       # 外观主题
```

## SSE 流式对话架构

APP 通过 OkHttp 直接发起 SSE 长连接（非 Retrofit），使用 `com.launchdarkly:okhttp-eventsource:4.1.1`：

| 客户端 | 端点 | 用途 |
|--------|------|------|
| `SseClient` | `POST /chat/stream` | 对话流式回复（CHAT / RAG 模式） |
| `SearchSseClient` | `POST /search/deep` | 联网深度搜索流式结果 |

SSE 事件通过 Kotlin Flow 发射，`ChatManager` 负责收集并更新 RecyclerView。

## 本地 Agent 命令路由

项目内置一套关键词/正则多级评分路由系统（`IntentRouter`），无需网络即可响应地质学常用命令：

| Agent | 触发条件 | 功能 |
|-------|----------|------|
| `UnitConversionAgent` | 用户输入单位换算关键词 | 地质学常用单位换算（深度/压力/温度/角度） |
| `RAG` | "文献/文档/知识库" 关键词 | 路由至 RAG 对话模式 |
| `SEARCH` | "联网/搜索/最新" 关键词 | 路由至联网深度搜索 |
| `DOCUMENT` | "上传/删除/文档列表" 关键词 | 路由至文档管理 |
| `SETTINGS` | "设置/主题/密码" 关键词 | 路由至设置/偏好 |
| `EMAIL` | "发邮件/email" 关键词 | 路由至邮件发送 |

**路由流程**：用户消息 → `ChatManager` → `IntentRouter.route()` → 命中则本地 dispatch，否则走 SSE 远端 API。

## 功能一览

| 功能 | 页面 | API |
|------|------|-----|
| 登录/注册/忘记密码 | Login / Register / ForgotPassword | `POST /auth/login`, `/auth/register`, `/auth/forgot-password` |
| SSE 流式对话（CHAT/RAG） | Chat Activity | `POST /chat/stream` (SSE) |
| 会话列表 & 历史 | Chat Drawer | `GET /chat/conversations`, `GET /chat/conversations/{id}/messages` |
| 追问建议 | Chat | `POST /chat/follow-up` |
| 图片消息 | Chat（选图按钮） | 随 SSE 流发送 Base64 |
| 地质单位换算 | Chat（本地 Agent） | 无网络调用（本地计算） |
| 知识库管理 | Document List / Detail | `GET /documents/list`, `POST /documents/upload-file`, `DELETE /documents/by-source/{name}` |
| 邮件发送 & 历史 | 设置 → 邮件 | `POST /email/send`, `GET /email/history` |
| 账号与安全 | Settings → 账号与安全 | `GET /auth/me`, `PUT /auth/me`, `POST /auth/change-password` |
| 主题外观 | Settings → 外观 | `GET /auth/preferences`, `PUT /auth/preferences` |

## 网络配置说明

`app/src/main/res/xml/network_security_config.xml` 仅对 `localhost` 和 `10.0.2.2` 放行明文 HTTP 流量，其他域名强制 HTTPS。

## 技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| 语言 | Kotlin | 2.0.0 |
| UI | Android Views (XML) + Material Components | 1.12.0 |
| 架构 | Repository 模式（无 ViewModel 层） | — |
| DI | Koin | 3.5.6 |
| 网络 | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| SSE | LaunchDarkly okhttp-eventsource | 4.1.1 |
| 内嵌服务 | NanoHTTPD | 2.3.1 |
| 本地存储 | DataStore Preferences | 1.1.1 |
| 图片 | Coil | 2.6.0 |
| Markdown | Markwon | 4.6.2 |