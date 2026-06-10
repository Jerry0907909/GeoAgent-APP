# GeoAgent Agent Router 扩展计划

> 基于 GeoAgent Android 项目的现有功能（Chat/RAG、Document、Search、Auth、Settings），规划一套**对话式 Agent 路由体系**。用户通过自然语言输入，系统根据关键词匹配路由到对应的 Agent 执行特定任务。
>
> **目标：** 不改变现有架构的前提下，以插件化方式扩展 Agent 能力。

---

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      User Input (Text/Voice)                │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    Intent Router (意图路由器)                │
│         关键词匹配 → 优先级排序 → 置信度阈值 → Agent 分发       │
└────────────────────┬────────────────────────────────────────┘
                     │
    ┌────────────────┼────────────────┬──────────────┐
    │                │                │              │
    ▼                ▼                ▼              ▼
┌───────┐      ┌───────┐      ┌───────┐      ┌───────────┐
│Agent 1│      │Agent 2│      │Agent 3│      │ Fallback  │
│Chat   │      │Doc    │      │Reminder│      │ GeneralQA │
│       │      │       │      │       │      │           │
└───────┘      └───────┘      └───────┘      └───────────┘
```

### 核心设计原则

- **无侵入式扩展**：每个 Agent 是一个独立模块，通过统一接口注册到 Router
- **关键词驱动路由**：Agent 声明自己的触发词表，Router 按优先级匹配
- **上下文感知**：支持多轮对话中保持当前 Agent 上下文，无需重复触发
- **降级策略**：无匹配时 fallback 到通用 Chat Agent（现有 SSE 对话）

---

## 2. 路由匹配策略

### 2.1 匹配层级

| 层级 | 匹配方式 | 示例 | 权重 |
|---|---|---|---|
| **L1 精确指令** | 指令前缀匹配（以 `/` 或 `@` 开头） | `/remind 明天下午3点开会` | 100 |
| **L2 关键词命中** | 问题中包含 Agent 核心关键词 | "帮我查一下文献" → RAGAgent | 80 |
| **L3 语义分类** | 简单 NLP 规则（动词+名词） | "定个闹钟" → 动词"定"+名词"闹钟" | 60 |
| **L4 上下文继承** | 当前会话已激活的 Agent | 用户刚用 ReminderAgent，后续问"再定一个" | 50 |
| **L5 Fallback** | 无匹配 → 通用 Chat | 任意非指令输入 | 0 |

### 2.2 置信度阈值

```
if 最高匹配分数 >= 80:   直接路由到对应 Agent
if 60 <= 分数 < 80:      询问用户确认（"您是希望我帮您...吗？"）
if 分数 < 60:            Fallback 到通用 Chat Agent
```

### 2.3 上下文保持机制

- 每个会话（conversation）维护一个 `activeAgent: String?` 字段
- 当 Agent 被激活时，设置 `activeAgent = "agent_name"`，并启动 TTL 计时器（如 5 分钟）
- 用户在 TTL 内的输入优先路由到同一个 Agent
- 用户输入 `/exit` 或发送非相关内容时，清除 `activeAgent`

---

## 3. Agent 清单（按优先级排序）

### 3.1 已有功能封装为 Agent（P0 - 高优先级）

#### Agent: `ChatAgent`（通用对话）
- **状态**：✅ 已实现（`ChatViewModel` + SSE）
- **职责**：通用地质知识问答、闲聊、解释概念
- **触发关键词**：无（作为默认 fallback）
- **指令前缀**：无
- **输入**：自然语言问题
- **输出**：SSE 流式回复
- **后端依赖**：`/chat/stream`（SiliconFlow API）
- **扩展点**：可作为其他 Agent 的 fallback，或 Agent 间调用的中间件

---

#### Agent: `RAGAgent`（知识库问答）
- **状态**：✅ 已实现（`ChatViewModel` 的 RAG 模式）
- **职责**：基于上传文档的地质文献检索与问答
- **触发关键词**："文献里提到"、"根据文档"、"资料中"、"RAG"、"检索知识库"
- **指令前缀**：`/rag`
- **输入**：关于文档内容的问题
- **输出**：带文献来源（Sources）的 SSE 流式回复
- **后端依赖**：`/chat/stream`（mode="rag"）
- **扩展点**：支持指定特定知识库 / Collection 进行检索

---

#### Agent: `SearchAgent`（深度搜索）
- **状态**：✅ 已实现（`SearchViewModel` + SSE）
- **职责**：联网深度搜索、多阶段信息提取与综合回答
- **触发关键词**："搜索"、"查一下"、"深度搜索"、"联网查"、"Tavily"
- **指令前缀**：`/search`
- **输入**：需要联网搜索的问题
- **输出**：Plan → Search → Extract → Answer → Citation 多阶段 SSE 流
- **后端依赖**：`/search/stream`（SiliconFlow + Tavily）
- **扩展点**：支持指定搜索语言、结果数量、时间范围

---

#### Agent: `DocumentAgent`（文档管理）
- **状态**：✅ 已实现（`DocumentViewModel`）
- **职责**：文档上传、删除、列表查看、内容预览
- **触发关键词**："上传文件"、"删除文档"、"查看文档"、"知识库"、"我的文档"
- **指令前缀**：`/doc`
- **输入**：文件选择 / 文档操作指令
- **输出**：操作结果确认（上传成功/删除确认等）
- **后端依赖**：`/documents/*`
- **扩展点**：
  - 支持语音指令 "上传最新的地质报告"
  - 支持文档批量操作
  - 支持按关键词在文档内搜索

---

#### Agent: `SettingsAgent`（设置与个人资料）
- **状态**：✅ 已实现（`SettingsViewModel`）
- **职责**：主题切换、修改昵称/头像、修改密码、偏好设置
- **触发关键词**："切换主题"、"修改密码"、"更新资料"、"设置"、"偏好"
- **指令前缀**：`/settings`
- **输入**：设置相关指令
- **输出**：设置页面跳转 / 操作确认
- **后端依赖**：`/auth/me`, `/auth/preferences`, `/auth/change-password`
- **扩展点**：语音指令 "打开深色模式"、"把字体调大"

---

### 3.2 新增 Agent（P1 - 中优先级）

#### Agent: `EmailAgent`（邮件助手）
- **状态**：🔄 待实现
- **职责**：发送邮件、查看发送历史、管理邮件模板
- **触发关键词**："发邮件"、"发送邮件给"、"邮件通知"、"SMTP"、"邮箱"
- **指令前缀**：`/email`
- **输入**：收件人、主题、正文（或语音生成）
- **输出**：发送成功/失败提示
- **后端依赖**：后端已有 QQ SMTP 配置（`/auth/send-verification-code` 证明后端具备发邮件能力）
- **前端新增**：
  - `EmailComposeScreen.kt` — 邮件撰写页
  - `EmailHistoryScreen.kt` — 发送历史
  - `EmailTemplateScreen.kt` — 模板管理
- **API 设计**：`POST /email/send`, `GET /email/history`
- **示例对话**：
  - 用户："发封邮件给张三，主题是这个月的地质报告"
  - Agent："好的，请确认邮件内容..." → 展示 compose 界面 → 发送

---

#### Agent: `ReminderAgent`（提醒与闹钟）
- **状态**：🔄 待实现
- **职责**：设置提醒、查看提醒列表、删除提醒、重复提醒
- **触发关键词**："提醒我"、"定个闹钟"、"设置提醒"、"到时叫我"、"倒计时"
- **指令前缀**：`/remind`
- **输入**：时间描述（自然语言，如"明天下午3点"、"30分钟后"）+ 提醒内容
- **输出**：提醒设置确认 + 系统通知
- **技术实现**：
  - Android `AlarmManager` / `WorkManager` 设置本地提醒
  - `NotificationManager` 触发通知
  - Room 数据库存储提醒列表
- **前端新增**：
  - `ReminderListScreen.kt`
  - `ReminderEditScreen.kt`
  - `ReminderReceiver.kt`（BroadcastReceiver）
- **示例对话**：
  - 用户："明天早上8点提醒我参加地质研讨会"
  - Agent：解析时间 → 设置 AlarmManager → 确认 "已为您设置提醒：明早8:00 参加地质研讨会"

---

#### Agent: `UnitConversionAgent`（地质单位换算）
- **状态**：🔄 待实现
- **职责**：地质学常用单位换算、坐标系转换、深度/压力/温度换算
- **触发关键词**："换算"、"转换单位"、"等于多少"、"米换英尺"、"坐标转换"
- **指令前缀**：`/convert`
- **输入**：数值 + 源单位 → 目标单位
- **输出**：换算结果 + 计算公式
- **技术实现**：
  - 前端本地计算（无需后端），使用预定义换算表
  - 支持常见地质单位：深度(m/ft)、压力(Psi/MPa)、温度(°C/°F/K)、角度(度/弧度/密位)
- **前端新增**：
  - `UnitConversionScreen.kt`
- **示例对话**：
  - 用户："3000米等于多少英尺"
  - Agent："3000米 ≈ 9842.52英尺"

---

### 3.3 高级 Agent（P2 - 低优先级 / 未来扩展）

#### Agent: `CalendarAgent`（日程管理）
- **职责**：查看日历、添加日程、查看当天/本周安排、会议提醒
- **触发关键词**："日程"、"日历"、"安排"、"会议"、"今天有什么"
- **指令前缀**：`/calendar`
- **技术实现**：
  - 读取 Android 系统日历（`CalendarContract` API）
  - 或自建轻量级日历（Room 存储）
- **与 ReminderAgent 的区别**：
  - ReminderAgent：一次性提醒，轻量级
  - CalendarAgent：日程管理，支持重复、邀请、地点

---

#### Agent: `DataAnalysisAgent`（数据分析）
- **职责**：分析上传文档中的数据（提取表格、绘制趋势图、统计分析）
- **触发关键词**："分析数据"、"画个趋势图"、"统计一下"、"提取表格"
- **指令前缀**：`/analyze`
- **技术实现**：
  - 后端：Python 数据分析服务（pandas + matplotlib）
  - 前端：Compose Canvas / MPAndroidChart 绘制图表
- **示例**：上传 CSV/Excel → 提取数据 → 绘制地质参数变化趋势图

---

#### Agent: `WeatherGeoAgent`（地质环境信息）
- **职责**：根据 GPS 位置获取当地天气、地质灾害预警、地震信息
- **触发关键词**："天气"、"地震"、"地质灾害"、"预警"、"这里的环境"
- **指令前缀**：`/env`
- **技术实现**：
  - 集成第三方 API（如中国地震台网、天气 API）
  - 获取设备 GPS 位置
- **示例**："北京今天有地质灾害预警吗？"

---

#### Agent: `ExportAgent`（导出与分享）
- **职责**：导出对话记录、分享文献片段、生成 PDF 报告
- **触发关键词**："导出"、"分享"、"生成报告"、"下载"、"PDF"
- **指令前缀**：`/export`
- **技术实现**：
  - 前端：使用 `PdfDocument` API 或第三方库生成 PDF
  - Android `FileProvider` + `Intent.ACTION_SEND` 分享

---

## 4. Agent Router 实现设计

### 4.1 数据结构设计

```kotlin
// Agent 元数据
interface AgentMeta {
    val name: String                    // 唯一标识，如 "reminder"
    val displayName: String             // 展示名，如 "提醒助手"
    val description: String             // 一句话描述
    val keywords: Set<String>         // 触发关键词（支持中文/英文/拼音）
    val commandPrefix: String?          // 指令前缀，如 "/remind"
    val priority: Int                   // 默认优先级（数字越小越高）
    val requiresAuth: Boolean           // 是否需要登录
    val ttlMinutes: Int                 // 上下文保持时间（分钟）
}

// 路由结果
data class RouteResult(
    val agentName: String,
    val confidence: Float,              // 0.0 ~ 1.0
    val extractedParams: Map<String, String> = emptyMap(),  // 从输入中提取的参数
    val reason: String                  // 路由原因（调试/日志用）
)
```

### 4.2 路由引擎核心逻辑

```
fun route(userInput: String, sessionContext: SessionContext): RouteResult {
    // 1. L1 精确指令匹配
    if (userInput startsWith "/") {
        return parseCommand(userInput)
    }
    
    // 2. 上下文继承（L4）
    if (sessionContext.activeAgent != null && sessionContext.isWithinTTL()) {
        val contextualConfidence = calculateContextualConfidence(userInput)
        if (contextualConfidence > 0.7) {
            return RouteResult(sessionContext.activeAgent, contextualConfidence, ..., "context_inherit")
        }
    }
    
    // 3. 关键词匹配（L2）
    val keywordMatches = agents.map { agent ->
        val matchCount = agent.keywords.count { keyword -> userInput.contains(keyword) }
        val confidence = min(matchCount * 0.2f + 0.4f, 0.95f)  // 每命中一个关键词+0.2，封顶0.95
        agent.name to confidence
    }
    
    // 4. 语义分类（L3）- 简单规则引擎
    val semanticMatch = semanticClassifier.classify(userInput)
    
    // 5. 取最高分
    val best = (keywordMatches + semanticMatch).maxBy { it.confidence }
    
    // 6. 应用阈值
    return when {
        best.confidence >= 0.8 -> RouteResult(best.agentName, best.confidence, ..., "keyword_match")
        best.confidence >= 0.6 -> RouteResult(best.agentName, best.confidence, ..., "keyword_match_ask_confirm")
        else -> RouteResult("chat", 1.0f, ..., "fallback_to_chat")
    }
}
```

### 4.3 Agent 生命周期

```
注册(Register) → 激活(Activate) → 执行(Execute) → 完成(Complete) → 休眠(Destroy after TTL)
                                      │
                                      └── 异常 → Fallback 到 ChatAgent
```

---

## 5. 实现优先级

### Phase 1：Agent Router 基础设施（1-2 周）
- [ ] 创建 `AgentMeta` 接口与注册机制
- [ ] 实现 `IntentRouter` 核心路由引擎
- [ ] 实现 `SessionContextManager`（上下文管理 + TTL）
- [ ] 将现有 Chat/RAG/Search/Document/Settings 功能封装为 Agent 接口
- [ ] 添加单元测试（路由匹配准确率）

### Phase 2：P1 Agent 实现（2-3 周）
- [ ] **EmailAgent**：邮件撰写、发送历史
- [ ] **ReminderAgent**：AlarmManager + 通知 + Room 存储
- [ ] **TranslationAgent**：LLM 翻译 + 历史记录
- [ ] **UnitConversionAgent**：本地计算 + 换算表

### Phase 3：P2 高级 Agent（后续迭代）
- [ ] **CalendarAgent**：系统日历集成
- [ ] **DataAnalysisAgent**：数据提取 + 图表绘制
- [ ] **WeatherGeoAgent**：第三方 API 集成
- [ ] **ExportAgent**：PDF 生成 + 分享

---

## 6. UI 交互设计

### 6.1 输入框增强

当前 `ChatComposer` 已有文本输入。扩展为支持：
- 输入 `/` 时弹出 Agent 快捷指令面板（类似 Slack/Discord）
- 检测到 Agent 关键词时，输入框左侧显示对应 Agent 图标

### 6.2 Agent 切换提示

当 Router 切换 Agent 时，在消息列表顶部显示横幅：
```
┌─────────────────────────────────────┐
│  🔔 已切换至「提醒助手」模式            │
│     输入 /exit 退出当前模式            │
└─────────────────────────────────────┘
```

### 6.3 Agent 专属 UI 卡片

某些 Agent 需要特殊 UI 组件：
- **ReminderAgent**：消息气泡内嵌提醒卡片（显示时间、开关按钮）
- **UnitConversionAgent**：消息气泡内嵌计算结果（大字号 + 公式）

---

## 7. 关键词词库（示例）

| Agent | 中文关键词 | 英文关键词 | 拼音 | 指令前缀 |
|---|---|---|---|---|
| ChatAgent | (默认，无) | chat, talk | liaotian | /chat |
| RAGAgent | 文献、文档、资料、检索、知识库 | document, literature | wenxian | /rag |
| SearchAgent | 搜索、查一下、联网、查找 | search, look up | sousuo | /search |
| DocumentAgent | 上传、删除、查看文档、文件 | upload, file | shangchuan | /doc |
| SettingsAgent | 设置、主题、密码、偏好 | settings, preference | shezhi | /settings |
| EmailAgent | 发邮件、邮箱、SMTP、通知 | email, mail | fayoujian | /email |
| ReminderAgent | 提醒、闹钟、定时、倒计时 | remind, alarm | tixing | /remind |
| UnitConversionAgent | 换算、转换、等于多少、单位 | convert, conversion | huansuan | /convert |
| UnitConversionAgent | 换算、转换、等于多少、单位 | convert, conversion | huansuan | /convert |
| CalendarAgent | 日程、日历、安排、会议 | calendar, schedule | richeng | /calendar |
| DataAnalysisAgent | 分析、统计、图表、趋势 | analyze, chart | fenxi | /analyze |
| WeatherGeoAgent | 天气、地震、灾害、预警 | weather, earthquake | tianqi | /env |
| ExportAgent | 导出、分享、PDF、报告 | export, share | daochu | /export |

---

## 8. 风险与注意事项

| 风险 | 应对策略 |
|---|---|
| 关键词误触发（如聊天中提到"搜索"误触发 SearchAgent） | 设置置信度阈值，低置信度时 fallback 到 ChatAgent 并提示确认 |
| Agent 间状态冲突 | 每个 Agent 独立管理自己的 state，通过 `SessionContext` 隔离 |
| 权限问题（Reminder 需要精确闹钟权限） | 首次使用时引导用户授权，未授权时优雅降级 |
| 多轮对话上下文丢失 | 使用 Room 持久化会话上下文，支持跨页面恢复 |
| 后端 API 未就绪 | 前端先 mock 实现，等后端对接 |

---

## 9. 总结

本计划为 GeoAgent Android App 设计了一套**可扩展的 Agent Router 体系**，核心要点：

1. **已有功能 Agent 化**：将现有 Chat、RAG、Search、Document、Settings 封装为统一 Agent 接口
2. **关键词路由**：通过多层级匹配策略（精确指令 → 关键词 → 语义 → 上下文 → Fallback）实现智能路由
3. **新增 8+ Agent**：覆盖邮件、提醒、翻译、单位换算、日程、数据分析、环境信息、导出等场景
4. **无侵入式设计**：Agent 以插件形式注册，不影响现有代码架构
5. **分阶段实现**：P0（基础设施）→ P1（实用 Agent）→ P2（高级 Agent）

**下一步建议**：先实现 Phase 1 的 Agent Router 基础设施和 1-2 个 P1 Agent（如 ReminderAgent 和 EmailAgent），验证架构可行性后再批量扩展。
