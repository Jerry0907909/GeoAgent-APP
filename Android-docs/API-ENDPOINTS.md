# GeoAgent Android — API 端点映射

> 后端 Base URL: `http://10.0.2.2:8000/api/` (Emulator)

## 0. 外部服务依赖

后端各 API 模块依赖的外部服务如下。Android APP 不直接调用这些服务，而是通过后端 API 间接使用。了解依赖关系有助于排查 API 调用失败时的错误原因。

| 后端模块 | 依赖的外部服务 | 关键 `.env` 变量 | 失败影响 |
|---|---|---|---|
| **Auth** | QQ SMTP (`smtp.qq.com:465`) | `SMTP_USER`, `SMTP_PASSWORD` | 无法发送邮箱验证码 |
| **Chat** | SiliconFlow API (`api.siliconflow.cn`) | `API_KEY`, `LLM_MODEL_NAME` | AI 对话失败，返回 500 |
| **Chat (RAG)** | SiliconFlow API + ChromaDB | `API_KEY`, `EMBEDDING_MODEL_NAME`, `CHROMA_PERSIST_DIR` | RAG 检索失败 |
| **Search** | SiliconFlow API + Tavily Search | `API_KEY`, `TAVILY_API_KEY` | 深度搜索失败 |
| **Documents** | SiliconFlow API (Embedding) | `API_KEY`, `EMBEDDING_MODEL_NAME` | 文档向量化失败 |
| **全局** | MySQL + Redis | `MYSQL_*`, `REDIS_*` | 用户数据/会话丢失 |

---

## 1. 认证模块 (Auth)

### POST `/auth/register`
注册账号

**Request:**
```json
{
  "username": "string",
  "email": "string",
  "password": "string",
  "verification_code": "string"
}
```

**Response:**
```json
{
  "id": 1,
  "username": "string",
  "email": "string",
  "access_token": "eyJhbGci...",
  "refresh_token": "eyJhbGci...",
  "token_type": "bearer"
}
```

---

### POST `/auth/login`
登录

**Request:**
```json
{
  "email": "string",
  "password": "string"
}
```

**Response:** `TokenResponse`
```json
{
  "access_token": "eyJhbGci...",
  "refresh_token": "eyJhbGci...",
  "token_type": "bearer",
  "expires_in": 1800
}
```

---

### POST `/auth/refresh`
刷新 Access Token

**Request:** Header `Authorization: Bearer {refresh_token}`

**Response:** `TokenResponse`

---

### POST `/auth/send-verification-code`
发送邮箱验证码

**外部依赖:** QQ SMTP (`smtp.qq.com:465`)。后端读取 `.env` 中的 `SMTP_USER`、`SMTP_PASSWORD`、`EMAIL_FROM` 发送邮件。若 SMTP 配置错误，此接口返回 500。

**Request:**
```json
{
  "email": "string"
}
```

**Response:**
```json
{
  "success": true,
  "message": "验证码已发送"
}
```

---

### GET `/auth/me`
获取当前用户信息

**Response:**
```json
{
  "id": 1,
  "username": "string",
  "email": "string",
  "full_name": "string",
  "avatar_url": "string",
  "is_active": true,
  "is_superuser": false
}
```

---

### PUT `/auth/me`
更新用户信息

**Request:**
```json
{
  "full_name": "string",
  "avatar_url": "string"
}
```

---

### POST `/auth/change-password`
修改密码

**Request:**
```json
{
  "old_password": "string",
  "new_password": "string",
  "confirm_password": "string"
}
```

---

## 2. 聊天模块 (Chat)

### POST `/chat/stream` — SSE 流式对话

**外部依赖:** SiliconFlow API (`api.siliconflow.cn`)。后端读取 `.env` 中的 `API_KEY`、`LLM_MODEL_NAME` 调用 LLM。若 API Key 失效，SSE 流会在中途返回 `type: "error"` 事件。

**Request:** Header `Authorization: Bearer {token}`
```json
{
  "message": "用户问题",
  "conversation_id": 123,
  "mode": "chat",        // "chat" | "rag"
  "top_k": 5,
  "min_relevance_score": 0.0,
  "web_search": false,
  "image_base64": "base64_encoded_image"  // 可选
}
```

**Response:** `text/event-stream`

SSE Event 类型:
```
data: {"type": "info", "conversation_id": 123}
data: {"type": "status", "message": "正在检索文献..."}
data: {"type": "content", "content": "回答片段..."}
data: {"type": "sources", "sources": [...]}
data: {"type": "done"}
data: {"type": "error", "message": "错误信息"}
```

---

### POST `/chat` — 非流式对话

**Request:** 同 `/chat/stream`

**Response:**
```json
{
  "answer": "AI回答",
  "sources": [
    {
      "content": "文献片段",
      "source": "文献标题",
      "relevance_score": 0.85
    }
  ],
  "conversation_id": 123
}
```

---

### POST `/chat/follow-up`
生成推荐问题

**Request:**
```json
{
  "question": "用户问题",
  "answer": "AI回答",
  "language": "zh"
}
```

**Response:**
```json
{
  "questions": [
    "推荐问题1",
    "推荐问题2",
    "推荐问题3"
  ]
}
```

---

## 3. 文档模块 (Documents)

### GET `/documents/list`
获取文档列表

**Query:** `?collection_name=xxx`

**Response:**
```json
{
  "documents": [
    {
      "id": "doc-uuid",
      "name": "文档名.pdf",
      "source": "地质构造.pdf",
      "type": "pdf",
      "size": 1024000,
      "created_at": "2026-05-20T10:00:00Z",
      "collection": "default"
    }
  ]
}
```

---

### POST `/documents/upload-file`
上传单个文档

**Request:** `Content-Type: multipart/form-data`
- `file`: 文件
- `collection_name`: 知识库名称

**Response:**
```json
{
  "document_id": "doc-uuid",
  "name": "文档名.pdf",
  "message": "上传成功"
}
```

---

### POST `/documents/upload-batch`
批量上传文档

**Request:** `Content-Type: multipart/form-data`
- `files[]`: 多个文件
- `collection_name`: 知识库名称

---

### DELETE `/documents/{doc_id}`
删除文档

**Response:**
```json
{
  "success": true,
  "message": "删除成功"
}
```

---

### GET `/documents/collections`
获取知识库列表

**Response:**
```json
{
  "collections": [
    {
      "name": "default",
      "document_count": 10,
      "created_at": "2026-05-20T10:00:00Z"
    }
  ]
}
```

---

## 4. 深度搜索模块 (Search)

### POST `/search/deep` — SSE 流式深度搜索

**Request:** Header `Authorization: Bearer {token}`
```json
{
  "query": "搜索问题"
}
```

**Response:** `text/event-stream`

SSE Event 类型:
```
data: {"type": "plan", "queries": [...]}
data: {"type": "search", "results": [...]}
data: {"type": "extract", "content": "提取内容"}
data: {"type": "answer", "content": "回答片段"}
data: {"type": "citation", "citations": [...]}
data: {"type": "done"}
```

---

## 5. 用户偏好模块

### GET `/auth/preferences`
获取用户偏好

**Response:**
```json
{
  "language": "zh-CN",
  "theme": "light",
  "default_model": "string",
  "max_context_messages": 10,
  "enable_memory": true
}
```

---

### PUT `/auth/preferences`
更新用户偏好

**Request:**
```json
{
  "language": "zh-CN",
  "theme": "dark",
  "enable_memory": true
}
```

---

## 6. DTO 定义

### TokenResponse
```kotlin
data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String,
    val expires_in: Int
)
```

### ChatEvent
```kotlin
sealed class ChatEvent {
    data class Info(val conversation_id: Int?) : ChatEvent()
    data class Status(val message: String) : ChatEvent()
    data class Content(val content: String) : ChatEvent()
    data class Sources(val sources: List<Source>) : ChatEvent()
    data class Done(val message: String?) : ChatEvent()
    data class Error(val message: String) : ChatEvent()
}
```

### Source
```kotlin
data class Source(
    val content: String,
    val source: String,
    val url: String? = null,
    val type: String = "document",  // "document" | "web"
    val relevance_score: Float? = null,
    val images: List<SourceImage> = emptyList()
)

data class SourceImage(
    val base64: String,
    val page: Int,
    val width: Int,
    val height: Int
)
```

### Document
```kotlin
data class Document(
    val id: String,
    val name: String,
    val source: String,
    val type: String,
    val size: Long,
    val created_at: String,
    val collection: String
)
```
