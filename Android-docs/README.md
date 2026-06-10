# GeoAgent Android — 技术设计文档

> 将 GeoAgent（AI 地质文献智能问答系统）移植为 Android APP。

## 项目概述

GeoAgent Android 是基于原生 Kotlin + Jetpack Compose 构建的移动端客户端，完整复刻 Web 端所有功能，通过 HTTP + SSE 与 FastAPI 后端通信。

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 语言 | Kotlin | 1.9+ |
| UI | Jetpack Compose | BOM 2024.02+ |
| 架构 | MVVM + Clean Architecture | — |
| 依赖注入 | Hilt | 2.50+ |
| 网络 | Retrofit + OkHttp + EventSource | 2.9 / 4.12 |
| 本地存储 | DataStore (偏好) / Room (缓存) | 1.0 / 2.6 |
| 图片加载 | Coil | 2.6 |
| 导航 | Jetpack Navigation Compose | 2.7+ |

## 模块结构（Clean Architecture）

```
app/
├── app/src/main/java/com/geoagent/
│   ├── GeoAgentApp.kt                 # Application entry
│   ├── MainActivity.kt                # Single Activity
│   ├── navigation/
│   │   └── GeoNavHost.kt              # Compose Navigation
│   ├── di/
│   │   ├── NetworkModule.kt           # Retrofit + OkHttp
│   │   ├── DatabaseModule.kt          # Room
│   │   └── RepositoryModule.kt        # Hilt binding
│   ├── ui/
│   │   ├── theme/                     # Material3 Theme + Color
│   │   ├── auth/
│   │   │   ├── LoginScreen.kt
│   │   │   ├── RegisterScreen.kt
│   │   │   └── AuthViewModel.kt
│   │   ├── chat/
│   │   │   ├── ChatListScreen.kt
│   │   │   ├── ChatDetailScreen.kt
│   │   │   └── ChatViewModel.kt
│   │   ├── documents/
│   │   │   ├── DocumentListScreen.kt
│   │   │   ├── UploadScreen.kt
│   │   │   └── DocumentViewModel.kt
│   │   ├── search/
│   │   │   ├── SearchScreen.kt
│   │   │   └── SearchViewModel.kt
│   │   └── settings/
│   │       ├── SettingsScreen.kt
│   │       ├── ProfileScreen.kt
│   │       └── SettingsViewModel.kt
│   ├── data/
│   │   ├── api/
│   │   │   ├── GeoAgentApi.kt         # Retrofit service
│   │   │   ├── SseClient.kt           # SSE event source
│   │   │   ├── AuthInterceptor.kt     # JWT Bearer attach
│   │   │   └── TokenAuthenticator.kt  # 401 refresh
│   │   ├── local/
│   │   │   ├── AppDatabase.kt         # Room
│   │   │   ├── TokenDataStore.kt      # DataStore
│   │   │   └── ConversationDao.kt     # Room DAO
│   │   └── repository/
│   │       ├── AuthRepositoryImpl.kt
│   │       ├── ChatRepositoryImpl.kt
│   │       ├── DocumentRepositoryImpl.kt
│   │       └── SearchRepositoryImpl.kt
│   └── domain/
│       ├── model/                     # 纯数据类
│       ├── repository/                # Interface
│       └── usecase/                   # UseCase
└── build.gradle.kts
```

## 快速开始

### 开发环境配置

1. **Android Studio** — 最新稳定版（Hedgehog 或更新）
2. **JDK** — 17+
3. **Android SDK** — API 33+（targetSdk=34）
4. **后端启动** — `conda activate RAG && python main.py`（确保后端在 :8000 运行）

### Emulator 端口转发

```bash
# 在 Android Studio 终端执行，让 Emulator 访问宿主机 :8000
adb reverse tcp:8000 tcp:8000
```

### 构建

```bash
./gradlew assembleDebug
```

## 核心功能模块

| 模块 | 主要页面 | 后端 API | 外部服务依赖 |
|---|---|---|---|
| **Auth** | LoginScreen / RegisterScreen | `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/send-verification-code` | QQ SMTP (邮箱验证码) |
| **Chat** | ChatListScreen / ChatDetailScreen | `POST /api/chat/stream` (SSE), `POST /api/chat`, `POST /api/chat/follow-up` | SiliconFlow API (LLM) |
| **Documents** | DocumentListScreen / UploadScreen | `GET /api/documents/list`, `POST /api/documents/upload-file`, `DELETE /api/documents/{id}` | — |
| **Search** | SearchScreen | `POST /api/search/deep` | Tavily Search API |
| **Settings** | SettingsScreen / ProfileScreen | `GET /api/auth/me`, `PUT /api/auth/preferences` | — |

> Android APP 本身不直接调用这些外部服务，而是通过后端 API 间接使用。后端部署时需要配置好对应的 `.env` 环境变量。

## 后端环境依赖配置

GeoAgent 后端依赖以下外部服务，在部署后端服务前需要在 `.env` 文件中配置：

### 1. LLM 服务（SiliconFlow API）

| 配置项 | `.env` 变量 | 说明 |
|---|---|---|
| API Key | `API_KEY` | SiliconFlow 统一 API Key（LLM + Embedding + Rerank 共用） |
| LLM Endpoint | `LLM_API_ENDPOINT` | `https://api.siliconflow.cn/v1/chat/completions` |
| LLM Model | `LLM_MODEL_NAME` | 当前配置：`Pro/moonshotai/Kimi-K2.6` |
| Temperature | `LLM_TEMPERATURE` | `0.1`（低随机性，适合学术问答） |
| Max Tokens | `LLM_MAX_TOKENS` | `2048` |
| Enable Thinking | `LLM_ENABLE_THINKING` | `false`（仅 DeepSeek V3/V4/R1 有效） |

### 2. Embedding 服务（SiliconFlow API）

| 配置项 | `.env` 变量 | 说明 |
|---|---|---|
| Embedding Endpoint | `EMBEDDING_API_ENDPOINT` | `https://api.siliconflow.cn/v1/embeddings` |
| Embedding Model | `EMBEDDING_MODEL_NAME` | `BAAI/bge-m3` |
| Dimension | `EMBEDDING_DIMENSION` | `1024` |

### 3. Rerank 服务（SiliconFlow API）

| 配置项 | `.env` 变量 | 说明 |
|---|---|---|
| Rerank Endpoint | `RERANK_API_ENDPOINT` | `https://api.siliconflow.cn/v1/rerank` |
| Rerank Model | `RERANK_MODEL_NAME` | `BAAI/bge-reranker-v2-m3` |

### 4. Web 搜索（Tavily Search API）

| 配置项 | `.env` 变量 | 说明 |
|---|---|---|
| API Key | `TAVILY_API_KEY` | 注册 `https://tavily.com` 获取，免费额度 1000 次/月 |

### 5. 邮箱验证（QQ SMTP）

| 配置项 | `.env` 变量 | 说明 |
|---|---|---|
| SMTP Host | `SMTP_HOST` | `smtp.qq.com` |
| SMTP Port | `SMTP_PORT` | `465` (SSL) |
| SMTP User | `SMTP_USER` | `1149201272@qq.com` |
| SMTP Password | `SMTP_PASSWORD` | QQ 邮箱 SMTP 授权码 |
| From | `EMAIL_FROM` | `1149201272@qq.com` |

### 6. 数据存储

| 配置项 | `.env` 变量 | 说明 |
|---|---|---|
| MySQL Host | `MYSQL_HOST` | `localhost` |
| MySQL Port | `MYSQL_PORT` | `3306` |
| MySQL User | `MYSQL_USER` | `root` |
| MySQL Password | `MYSQL_PASSWORD` | 数据库密码 |
| MySQL Database | `MYSQL_DATABASE` | `geology_agent` |
| Chroma Directory | `CHROMA_PERSIST_DIR` | `./data/chroma_db` |
| Chroma Collection | `CHROMA_COLLECTION_NAME` | `qa_dataset` |
| Redis Host | `REDIS_HOST` | `localhost` |
| Redis Port | `REDIS_PORT` | `6379` |

### 7. JWT 鉴权

| 配置项 | `.env` 变量 | 说明 |
|---|---|---|
| Secret Key | `JWT_SECRET_KEY` | 签名密钥（生产环境务必修改） |

## 关键技术点

### 1. SSE 流式对话

OkHttp EventSource 订阅 `/api/chat/stream`，逐行解析 `data:` 前缀的 JSON，通过 `Flow<ChatEvent>` 驱动 Compose UI。

### 2. Token 自动刷新

`TokenAuthenticator` 拦截 401 → 调用 `/api/auth/refresh` → 更新 token → 重试原请求。token 过期时用户无感知。

### 3. 主题切换

Material3 Dynamic Color（Android 12+）+ 自定义 light/dark palette，与 GeoAgent 品牌色 `#b7c8fe` / `#f4f7ff` 保持一致。

## 文件索引

| 文件 | 内容 |
|---|---|
| `TECHNICAL-SPEC.md` | 完整技术规范（架构、组件、数据流） |
| `API-ENDPOINTS.md` | 后端 API 端点详细映射与 DTO 定义 |
| `UI-SPEC.md` | 各页面 UI 规范、状态管理、交互设计 |

---

*Generated for GeoAgent Android Port. Last updated: 2026-05-25.*
