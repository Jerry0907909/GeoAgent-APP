# GeoAgent Animation Plan

> 目标：为 GeoAgent Android App 引入类似 React Framer Motion 的优雅动画体系，覆盖页面导航切换、AI 流式输出、消息气泡入场、组件交互反馈等场景。
>
> **参考对标**：Framer Motion 的 `AnimatePresence` + `motion.div` + `layoutId` + `variants` 体系。在 Compose 中通过 `AnimatedContent`、`animatedContentSize`、`animate*AsState`、`SharedTransition` 等 API 达到同等效果。

---

## 1. 当前动画缺失诊断

| 场景 | 当前表现 | 问题 |
|---|---|---|
| **NavHost 页面切换** | 硬切，无任何过渡 | 用户感知不到导航层级变化 |
| **Splash → Login/Main** | 硬切，Logo 无动效 | 缺乏品牌质感，启动体验生硬 |
| **消息气泡入场** | 直接出现 | 缺乏即时通讯的流畅感 |
| **AI 流式输出** | 文本直接 append，无动画 | 用户感知不到"逐字思考"的过程 |
| **按钮交互** | 无按压反馈、无涟漪 | 缺乏 Material3 的触感反馈 |
| **列表项加载** | 直接出现 | 缺乏数据加载的渐进式体验 |
| **Drawer 展开** | 默认滑动，无弹性 | 可以加入弹性插值 |

---

## 2. 动画设计原则

### 2.1 物理模型

```
Duration: 短交互 150-200ms，页面切换 300-400ms，入场动画 500-800ms
Easing:   出场 ease-in，入场 ease-out，布局变化 spring
Spring:   阻尼比 0.8-1.0（轻微过冲增加活力），刚度 300-500
Stagger:  列表项 30-50ms 间隔
Opacity:  配合位移使用，避免单独使用
```

### 2.2 Compose 动画 API 选型

| Framer Motion 概念 | Compose 等价物 | 用途 |
|---|---|---|
| `AnimatePresence` | `AnimatedContent` + `AnimatedVisibility` | 页面/组件的进入/退出动画 |
| `motion.div` | `animate*AsState` + `Modifier.animateContentSize()` | 属性驱动的动态动画 |
| `layoutId` | `SharedTransition` (Compose 1.7+) | 跨页面的共享元素动画 |
| `variants` | `updateTransition` + `TransitionState` | 组合式动画状态机 |
| `spring` | `androidx.compose.animation.core.spring()` | 弹性物理动画 |
| `staggerChildren` | 自定义延迟的 `LaunchedEffect` | 列表项错开动画 |

---

## 3. 页面导航切换动画

### 3.1 目标效果（对标 Framer Motion AnimatePresence）

```
NavHost 切换：
  当前页面 → 向右滑出 + 淡出（exit: { x: 100%, opacity: 0 }）
  新页面   → 从右滑入 + 淡入（enter: { x: -30%, opacity: 1 }）
  返回时反向（exit 向左，enter 从左）
```

### 3.2 实现方案

Compose Navigation 的 `composable()` 不支持直接的 enter/exit 动画，但可以通过 `AnimatedContent` + 自定义 `NavHost` 包装实现：

```kotlin
// 方案 A：自定义 AnimatedNavHost（推荐）
@Composable
fun AnimatedNavHost(navController: NavHostController) {
    val currentRoute by navController.currentBackStackEntryFlow
        .collectAsState(initial = null)
    
    AnimatedContent(
        targetState = currentRoute?.destination?.route,
        transitionSpec = {
            // 根据路由判断是前进还是后退
            val isForward = determineDirection(initialState, targetState)
            if (isForward) {
                slideInHorizontally { fullWidth -> fullWidth } + fadeIn() togetherWith
                slideOutHorizontally { fullWidth -> -fullWidth } + fadeOut()
            } else {
                slideInHorizontally { fullWidth -> -fullWidth } + fadeIn() togetherWith
                slideOutHorizontally { fullWidth -> fullWidth } + fadeOut()
            }
        },
        label = "nav_transition"
    ) { route ->
        // 渲染对应页面
    }
}
```

### 3.3 各页面切换效果设计

| 切换方向 | 动画效果 | Duration | Easing |
|---|---|---|---|
| Splash → Login | Logo 缩放淡出 + Login 从下方滑入 | 500ms | `easeOutCubic` |
| Splash → Main | Logo 缩放淡出 + Main 从下方滑入 | 500ms | `easeOutCubic` |
| Login → Register | 横向滑动（左出右进） | 300ms | `spring(stiffness=300, dampingRatio=0.8)` |
| Login → Main | 缩放 + 淡入（类似 iOS App 打开） | 400ms | `spring(stiffness=400, dampingRatio=0.75)` |
| Main → Settings | 从右向左滑入（iOS Navigation 风格） | 300ms | `spring(stiffness=400, dampingRatio=0.8)` |
| Settings → Main | 从左向右滑出（返回） | 300ms | `spring(stiffness=400, dampingRatio=0.8)` |
| Chat List → Chat Detail | 共享元素动画（列表项 → 详情页标题） | 400ms | `spring` |
| Main → Documents | 从右向左滑入 | 300ms | `spring(stiffness=400)` |

### 3.4 共享元素过渡（Shared Element Transition）

当从 Chat List 点击某条对话进入 Chat Detail 时，使用 Compose 1.7+ 的 `SharedTransition`：

```kotlin
// ChatListItem 中的共享元素
SharedElement(
    key = "conversation_${conversation.id}",
    animatedVisibilityScope = this
) {
    Text(
        text = conversation.title,
        modifier = Modifier.sharedElement()
    )
}

// ChatDetailScreen 中的对应元素
SharedElement(
    key = "conversation_${conversation.id}",
    animatedVisibilityScope = this
) {
    Text(
        text = conversation.title,
        modifier = Modifier.sharedElement()
    )
}
```

---

## 4. AI 流式输出动画

### 4.1 当前问题

AI 回复目前是直接 `append` 到字符串，文本瞬间完整出现，用户感知不到"思考过程"。

### 4.2 目标效果（对标打字机效果）

```
1. AI 回复区域先出现淡入的光标闪烁 ✓
2. 每个字/词逐个出现，带轻微打字声（可选）
3. 新出现的文字从下方微微弹入（translateY + alpha）
4. Markdown 渲染元素（标题、列表、代码块）出现时带 Stagger 动画
5. Sources 卡片从底部滑入
```

### 4.3 逐字显示动画实现

```kotlin
@Composable
fun StreamingText(
    fullText: String,
    charDelayMillis: Long = 16,  // ~60fps 的打字速度
    onComplete: (() -> Unit)? = null
) {
    var displayedLength by remember { mutableIntStateOf(0) }
    val isComplete = displayedLength >= fullText.length
    
    LaunchedEffect(fullText) {
        displayedLength = 0
        fullText.indices.forEach { index ->
            delay(charDelayMillis)
            displayedLength = index + 1
        }
        onComplete?.invoke()
    }
    
    val displayed = fullText.take(displayedLength)
    
    // 使用 AnimatedVisibility 让新字符有入场动画
    Text(
        text = displayed,
        modifier = Modifier.animateContentSize(
            animationSpec = spring(stiffness = 400, dampingRatio = 0.8)
        )
    )
}
```

### 4.4 增强版 —— 逐词动画 + 光标闪烁

```kotlin
@Composable
fun StreamingTextWithCursor(
    fullText: String,
    wordsPerSecond: Float = 8f,
    cursorBlinkInterval: Long = 530
) {
    val words = remember(fullText) { fullText.split(" ") }
    var visibleWordCount by remember { mutableIntStateOf(0) }
    var isCursorVisible by remember { mutableStateOf(true) }
    
    // 逐词显示
    LaunchedEffect(fullText) {
        visibleWordCount = 0
        val delayPerWord = (1000 / wordsPerSecond).toLong()
        words.indices.forEach { index ->
            delay(delayPerWord)
            visibleWordCount = index + 1
        }
    }
    
    // 光标闪烁
    LaunchedEffect(Unit) {
        while (true) {
            delay(cursorBlinkInterval)
            isCursorVisible = !isCursorVisible
        }
    }
    
    val visibleText = words.take(visibleWordCount).joinToString(" ")
    val isComplete = visibleWordCount >= words.size
    
    Row {
        Text(text = visibleText)
        if (!isComplete || isCursorVisible) {
            // 光标
            Text(
                text = "▋",
                color = BrandPrimary,
                modifier = Modifier.alpha(if (isComplete) 0.6f else 1f)
            )
        }
    }
}
```

### 4.5 Markdown 元素 Stagger 入场

当 AI 回复包含 Markdown（标题、列表、代码块等）时，各元素按顺序错开动画入场：

```kotlin
@Composable
fun AnimatedMarkdownContent(
    markdown: String,
    animationDelay: Long = 0
) {
    val elements = remember(markdown) { parseMarkdown(markdown) }
    
    Column {
        elements.forEachIndexed { index, element ->
            val visible = remember { Animatable(0f) }
            
            LaunchedEffect(Unit) {
                delay(animationDelay + index * 60L)  // Stagger 60ms
                visible.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(stiffness = 300, dampingRatio = 0.8)
                )
            }
            
            AnimatedVisibility(
                visible = visible.value > 0.5f,
                enter = fadeIn() + slideInVertically { it / 3 }
            ) {
                RenderMarkdownElement(element)
            }
        }
    }
}
```

### 4.6 Sources 卡片入场动画

```kotlin
@Composable
fun SourcesCard(sources: List<SourceDto>) {
    AnimatedVisibility(
        visible = sources.isNotEmpty(),
        enter = expandVertically(
            animationSpec = spring(stiffness = 300, dampingRatio = 0.8)
        ) + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            sources.forEachIndexed { index, source ->
                val offset = remember { Animatable(30f) } // 从下方 30dp 滑入
                
                LaunchedEffect(Unit) {
                    delay(index * 80L)  // Stagger
                    offset.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(stiffness = 400, dampingRatio = 0.75)
                    )
                }
                
                SourceCard(
                    source = source,
                    modifier = Modifier.offset(y = offset.value.dp)
                )
            }
        }
    }
}
```

---

## 5. 消息气泡入场动画

### 5.1 用户消息气泡

```kotlin
// 用户发送消息后，气泡从右侧滑入 + 缩放
val enterAnimation = slideInHorizontally { fullWidth -> fullWidth } + 
                     scaleIn(initialScale = 0.85f) + 
                     fadeIn()

// 具体实现
AnimatedVisibility(
    visible = true,
    enter = slideInHorizontally { it } + scaleIn(initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 0.5f)) + fadeIn(),
    exit = slideOutHorizontally { it } + scaleOut(targetScale = 0.85f) + fadeOut()
)
```

### 5.2 AI 消息气泡

```kotlin
// AI 回复气泡从左侧滑入，带轻微弹性
val aiEnterAnimation = slideInHorizontally { fullWidth -> -fullWidth } + 
                       scaleIn(initialScale = 0.95f) + 
                       fadeIn()

// 列表项间的 stagger
LazyColumn {
    itemsIndexed(messages, key = { _, msg -> msg.timestamp }) { index, message ->
        val itemDelay = index * 50L  // 每个消息错开 50ms
        
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(400, delayMillis = itemDelay.toInt(), easing = EaseOutCubic)
            ) + fadeIn(tween(300, delayMillis = itemDelay.toInt()))
        ) {
            ChatMessageBubble(message = message)
        }
    }
}
```

### 5.3 新消息自动滚动动画

```kotlin
// 当前：直接跳转到最底部
listState.animateScrollToItem(itemCount - 1)

// 优化：平滑滚动 + 弹性到达
LaunchedEffect(messages.size) {
    listState.animateScrollToItem(
        index = messages.lastIndex,
        scrollOffset = 0,
        // 使用自定义动画
        animationSpec = spring(stiffness = 200f, dampingRatio = 0.85f)
    )
}
```

---

## 6. 组件交互动画

### 6.1 按钮按压反馈（Ripple + Scale）

```kotlin
@Composable
fun AnimatedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = 400, dampingRatio = 0.5f),
        label = "button_scale"
    )
    
    IconButton(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> isPressed = true
                            PointerEventType.Release -> isPressed = false
                        }
                    }
                }
            }
    ) {
        content()
    }
}
```

### 6.2 输入框发送按钮动画

```kotlin
// 当前：直接切换背景色和图标颜色
// 优化：缩放 + 颜色渐变 + 涟漪
val sendButtonScale by animateFloatAsState(
    targetValue = if (isSendable) 1.05f else 1f,
    animationSpec = spring(stiffness = 500, dampingRatio = 0.6f)
)

val sendButtonColor by animateColorAsState(
    targetValue = if (isSendable) BrandPrimary else BrandSoft,
    animationSpec = tween(200)
)
```

### 6.3 Drawer 展开弹性动画

```kotlin
// 当前：默认线性滑动
// 优化：带弹性的滑动 + 内容 Stagger
val drawerProgress by animateFloatAsState(
    targetValue = if (drawerState.isOpen) 1f else 0f,
    animationSpec = spring(stiffness = 300, dampingRatio = 0.8f)
)
```

---

## 7. 全局动画工具

### 7.1 Animation Utils

```kotlin
// ui/animation/AnimationUtils.kt

object GeoAnimations {
    // 页面切换动画
    val PageEnter = fadeIn(animationSpec = tween(300)) + 
                    slideInHorizontally { it }
    
    val PageExit = fadeOut(animationSpec = tween(200)) + 
                   slideOutHorizontally { -it }
    
    // 列表项入场动画
    val ListItemEnter = fadeIn(tween(300)) + 
                        slideInVertically { it / 5 }
    
    // 弹窗入场
    val DialogEnter = fadeIn(tween(200)) + 
                      scaleIn(initialScale = 0.9f, tween(300))
    
    // Spring 配置
    val SpringSoft = spring<Float>(stiffness = 300f, dampingRatio = 0.8f)
    val SpringBouncy = spring<Float>(stiffness = 400f, dampingRatio = 0.6f)
    val SpringStiff = spring<Float>(stiffness = 500f, dampingRatio = 0.9f)
}
```

### 7.2 自定义 Easing

```kotlin
// ui/animation/Easing.kt
val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
val EaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)  // 带回弹
```

---

## 8. 性能考虑

| 场景 | 策略 |
|---|---|
| **频繁重绘** | 使用 `remember` + `derivedStateOf` 避免不必要的 recomposition |
| **大量列表项** | `LazyColumn` 已默认做 viewport 内渲染，配合 `key` 避免全量刷新 |
| **复杂动画** | 使用 `AnimatedContent` 的 `contentKey` 减少不必要的动画触发 |
| **后台动画** | 页面不可见时暂停动画（`LifecycleOwner` 感知） |
| **低端设备** | 检测 `Build.VERSION` 和硬件性能，降级为简单 fade |

---

## 9. 实现优先级

### Phase 1：核心动画（高优先级）
- [ ] **NavHost 页面切换动画**（Splash → Login → Main 的过渡）
- [ ] **AI 流式输出逐字动画**（StreamingText + Cursor 闪烁）
- [ ] **消息气泡入场动画**（用户气泡从右滑入，AI 气泡从左滑入）

### Phase 2：交互增强（中优先级）
- [ ] **按钮按压 Scale 反馈**
- [ ] **发送按钮状态动画**（缩放 + 颜色渐变）
- [ ] **Drawer 弹性展开**
- [ ] **Sources 卡片 Stagger 入场**

### Phase 3：高级动画（低优先级 / 未来扩展）
- [ ] **共享元素过渡（SharedTransition）**
- [ ] **Markdown 元素 Stagger 入场**
- [ ] **列表滚动惯性动画优化**
- [ ] **深色/浅色主题切换过渡动画**

---

## 10. 参考实现

| 动画效果 | Compose API | 参考文件 |
|---|---|---|
| 页面切换 | `AnimatedContent` + `slideIn/slideOut` | `navigation/AnimatedNavHost.kt` |
| 逐字显示 | `Animatable` + `delay` | `components/StreamingText.kt` |
| 消息入场 | `AnimatedVisibility` + `slideInHorizontally` | `components/ChatMessageBubble.kt` |
| 弹性缩放 | `animateFloatAsState` + `spring()` | `components/AnimatedButton.kt` |
| 内容尺寸变化 | `Modifier.animateContentSize()` | `components/ExpandableCard.kt` |
| 共享元素 | `SharedTransition` | `navigation/SharedElementNav.kt` |

---

## 11. 总结

本 Animation Plan 为 GeoAgent 设计了一套对标 React Framer Motion 的动画体系：

1. **页面导航**：通过 `AnimatedContent` 包装 NavHost，实现类似 `AnimatePresence` 的 enter/exit 动画
2. **AI 流式输出**：`StreamingText` 组件实现逐字/逐词打字机效果 + 光标闪烁
3. **消息气泡**：`slideInHorizontally` + `scaleIn` + `fadeIn` 组合，列表项 Stagger 入场
4. **交互反馈**：按钮按压弹性缩放、发送按钮状态渐变、Drawer 弹性展开
5. **全局工具**：统一的 `AnimationUtils` + `Easing` 定义，保持动画一致性

**下一步建议**：先实现 Phase 1 的核心动画（NavHost 切换 + StreamingText + 消息入场），验证性能和体验后再扩展到 Phase 2/3。
