## 目标
- 在 Compose Multiplatform 上提供一个可无限平移/缩放的画布系统，具备可视区域外项目的懒加载与回收（虚拟化）。
- 明确区分“逻辑坐标”和“渲染坐标”，画布尺寸理论上无限；支持获取当前逻辑位置与可视窗口。
- 实现缩放联动：不对父容器做 `Modifier.scale`，而由画布维护缩放与变换并将参数下发到子元素，子元素自行决定尺寸/位置随缩放如何变化。

## 总体架构
- 模块划分：
  - `lcanvas-core`：核心画布（状态、坐标变换、虚拟化、手势、接口）。
  - `lcanvas-components`：基础节点/连线/分组等可复用 UI 组件（首期可选）。
  - `lcanvas-samples`：示例与性能演示（用于 `composeApp` 集成）。
- 依赖选择：
  - 复用 MinaBox 的二维 Lazy 虚拟化能力（项目已引入 `io.github.oleksandrbalan:minabox`），在其之上封装我们的画布 API；后续可根据需要将虚拟化替换为自研 `LazyLayout` 层。
- 平台兼容：Compose Multiplatform（Android/iOS/JVM/Web/Wasm），统一用 `pointerInput` 与 `detectTransformGestures` 管理平移+缩放。

## 核心概念与数据结构
- `CanvasState`（可组合记忆状态）：
  - 字段：`scale: Float`、`translation: Offset`（渲染空间位移）、`viewportSize: IntSize`、`overscan: Dp/px`。
  - 方法：
    - `logicToRender(logic: Offset | Rect)` / `renderToLogic(render: Offset | Rect)`。
    - `viewportLogicRect(): Rect`（当前可视窗口对应的逻辑坐标矩形）。
    - `anchorZoom(focalRender: Offset, scaleDelta: Float)`（以手势焦点为锚进行缩放，保持锚点逻辑位置稳定）。
    - `pan(deltaRender: Offset)`（渲染空间平移，自动映射为逻辑变化）。
- `CanvasTransform`：封装逻辑→渲染变换矩阵与逆矩阵（含缩放、平移），对外只暴露必要 API，避免直接使用父级 `scale`。
- `CanvasItemLayout`：
  - 逻辑空间中的位置与尺寸：`x`, `y`, `width`, `height`（支持绝对像素与相对父尺寸两种模式）。
  - 可选：锁定横/纵（类似 MinaBox 的 pinned 行/列）。
- `CanvasChildScope`：下发到子项的上下文（只读）：
  - `transform: CanvasTransform`、`scale: Float`（当前画布缩放）、`renderRect: Rect`（渲染空间矩形）、`logicRect: Rect`（逻辑空间矩形）。
  - 子元素据此自行决定字体、描边、阴影等如何随缩放变化。

## 公共 API（草案）
- 记忆状态：
  - `@Composable fun rememberCanvasState(initialScale: Float = 1f, initialTranslation: Offset = Offset.Zero, overscanPx: Float = 256f): CanvasState`
- 画布容器：
  - `@Composable fun CanvasBox(state: CanvasState, modifier: Modifier = Modifier, content: CanvasScope.() -> Unit)`
    - 内部处理指针手势（平移/缩放）、刷新视口、驱动虚拟化。
- 子项注册（参考 MinaBox 风格）：
  - `fun CanvasScope.items(count: Int, key: ((Int) -> Any)? = null, layoutInfo: (Int) -> CanvasItemLayout, itemContent: @Composable CanvasChildScope.(index: Int) -> Unit)`
  - 或支持 `items(list: List<T>, key: ((T) -> Any)? = null, layoutInfo: (T) -> CanvasItemLayout, itemContent: @Composable CanvasChildScope.(item: T) -> Unit)`。
- 视口回调与逻辑位置查询：
  - `state.viewportLogicRect()` 获取当前逻辑窗口。
  - `onViewportChanged: (Rect) -> Unit` 回调（`CanvasBox` 参数）便于外部同步逻辑位置。

## 虚拟化设计
- 基于 MinaBox：
  - 使用 MinaBox 的 `items` 注册接口，`layoutInfo` 返回 `MinaBoxItem(x, y, width, height)`，这些值由我们的 `CanvasTransform` 从逻辑空间映射为渲染空间（像素）。
  - 通过 `overscan` 提前加载视口周围一定范围，平滑滚动与缩放。
  - 可视范围外的元素不 compose，MinaBox 负责回收/复用。
- 可替换实现：后续用 `LazyLayout` 复刻二维虚拟化层，保持相同 API。

## 手势与缩放联动
- 使用 `pointerInput` + `detectTransformGestures`：
  - 缩放：以手势焦点为锚点进行 `anchorZoom`；不使用 `Modifier.scale`，而是更新 `CanvasState.scale` 与 `translation`。
  - 平移：拖动更新 `translation`；支持动量/阻尼（可选）。
- 下发到子项：
  - 通过 `CanvasChildScope` 提供 `scale` 与 `renderRect`，子项根据自身策略决定视觉尺寸与排版（例如：随缩放调整字体大小、线宽上限/下限等）。

## 坐标与变换策略
- 逻辑空间：以像素为单位或自定义逻辑单位（初期用像素），画布无限。
- 渲染空间：容器实际像素尺寸；MinaBox 的 item 布局使用渲染空间坐标。
- 变换关系：`render = logic * scale + translation`；逆变换用于命中测试与对外暴露逻辑位置。
- 视口定义：渲染空间 `[0..viewportSize]` 通过逆变换得到逻辑窗口矩形。

## 性能与可扩展
- 稳定 key：`items` 要求稳定 key，避免重组抖动。
- 派生状态：变换与视口都使用 `derivedStateOf` 合理缓存。
- 空间索引（可选）：大量元素时引入 R-Tree/KD-Tree 做粗筛，MinaBox 渲染前过滤出可能可见项。
- Overscan：可配置像素值，兼顾滚动速度和内存占用。

## 组件层（可选首期）
- 基础节点 `Node`：可接收 `CanvasChildScope`，展示标题/内容，支持选中与拖拽。
- 端口与连线：连接两个 `Node` 的锚点，曲线或折线，随缩放联动粗细与命中范围。
- 分组与层级：提供 `zIndex` 管控与选区框；为后续 ComfyUI 类能力（多组选择/嵌套）打基础。

## 使用示例（草案）
```kotlin
@Composable
fun Demo() {
    val state = rememberCanvasState(overscanPx = 512f)
    CanvasBox(state, Modifier.fillMaxSize()) {
        val columns = 200
        val rows = 200
        items(count = columns * rows,
              layoutInfo = { i ->
                  val x = (i % columns) * 128f
                  val y = (i / columns) * 64f
                  CanvasItemLayout(x = x, y = y, width = 128f, height = 64f)
              }
        ) { index ->
            // 在此接收 CanvasChildScope：scale/renderRect/logicRect
            // 子项自行控制随缩放的文本、描边、间距等
            Cell(index = index, scale = scale, rect = renderRect)
        }
    }
}
```

## 集成步骤
- 新增 `lcanvas-core` 模块（KMP Library）：
  - 依赖 `compose.foundation`、`compose.runtime`、`minabox`。
  - 暴露 API：`CanvasState`、`CanvasBox`、`CanvasScope.items` 等。
- 在 `composeApp/src/commonMain/kotlin/com/lalilu/lcanvas/App.kt` 增加示例入口（不影响现有内容）。
- Gradle：在根与 `composeApp` 保持 `mavenCentral()`；确保 MinaBox 版本与 Compose 版本兼容。

## 验证方案
- 逻辑单元测试：`logicToRender`/`renderToLogic` 精度与可逆性；`anchorZoom` 锚点稳定性。
- 交互验证：平移/缩放流畅；放大后子项尺寸/位置的联动正确。
- 虚拟化验证：大规模元素（>10k）在快速平移/缩放时只渲染视口与 overscan 范围；内存与重组次数可控。
- 构建与语法：本地 Gradle build；IDE 语法检查与问题修复（按需提示优化建议）。

## 交付物
- `lcanvas-core` 源码与 KDoc。
- 示例页面与对比演示（网格/节点/连线）。
- 文档：逻辑/渲染坐标、变换规则、扩展点说明、性能参数（overscan、key、索引）。

## 后续迭代（方向）
- 图编辑能力：框选、多选、组合、撤销/重做、对齐辅助线、吸附。
- 高级虚拟化：自研 `LazyLayout` 二维层替换 MinaBox，细粒度预取与测量缓存。
- Web/Wasm 适配细化：指针与滚轮行为在浏览器端的差异化优化。
