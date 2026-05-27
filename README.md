# GeoAgent Android

AI 地质文献智能问答系统 Android 客户端（Jetpack Compose）。

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
├── MainActivity.kt               # 唯一 Activity，Compose 入口
├── navigation/                   # 导航图
│   ├── Routes.kt                 # 路由常量
│   ├── GeoNavHost.kt             # 顶层 NavHost（Splash→Auth→Main）
│   └── BottomNavBar.kt           # （历史遗留，可移除）
├── di/                           # Koin 依赖注入模块
│   ├── NetworkModule.kt          # OkHttp + Retrofit + HTTP 缓存
│   ├── RepositoryModule.kt       # Repository 接口→实现绑定
│   ├── DataStoreModule.kt        # TokenDataStore / UserPrefsDataStore
│   └── ViewModelModule.kt        # ViewModel 注入
├── domain/
│   ├── model/Models.kt           # User, Message, Conversation 等
│   └── repository/               # Repository 接口（4 个）
├── data/
│   ├── api/                      # Retrofit 接口 + SSE Client + 拦截器 + 认证器
│   ├── api/dto/                  # 请求/响应 DTO + SSE 事件
│   ├── local/TokenDataStore.kt   # Token 持久化（DataStore Preferences）
│   └── repository/               # Repository 实现（4 个）
└── ui/
    ├── theme/                    # Corporate Clean 风格主题（浅色/深色）
    ├── splash/SplashScreen.kt    # 启动页（token 检查 + 自动跳转）
    ├── auth/                     # 登录 + 注册 + AuthViewModel
    ├── main/MainScreen.kt        # 抽屉导航壳 + 内嵌 NavHost
    ├── chat/                     # 对话列表 + SSE 流式对话 + ChatViewModel
    │   └── components/           # 消息气泡、输入框、TogglePill、来源展示
    ├── documents/                # 文档列表 + 上传（DocumentViewModel）
    └── settings/                 # 设置（账号与安全 / 外观）
```

## 功能一览

| 功能 | 页面 | API |
|------|------|-----|
| 登录/注册 | Login / Register | `POST /auth/login`, `/auth/register` |
| SSE 流式对话 | Chat Detail | `POST /chat/stream` (SSE) |
| 知识库管理 | Documents | `GET/POST/DELETE /documents/*` |
| 账号与安全 | Settings → 账号与安全 | `GET /auth/me`, `PUT /auth/me`, `POST /auth/change-password` |
| 主题外观 | Settings → 外观 | `PUT /auth/preferences` |

## 网络配置说明

`app/src/main/res/xml/network_security_config.xml` 仅对 `localhost` 和 `10.0.2.2` 放行明文 HTTP 流量，其他域名强制 HTTPS。

## 技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| 语言 | Kotlin | 2.0.0 |
| UI | Jetpack Compose + Material3 | BOM 2024.06 |
| 架构 | MVVM + Repository 模式 | — |
| DI | Koin | 3.5.6 |
| 网络 | Retrofit + OkHttp | 2.11 / 4.12 |
| 内嵌服务 | NanoHTTPD | 2.3.1 |
| 本地存储 | DataStore Preferences | 1.1.1 |
| 图片 | Coil Compose | 2.6.0 |