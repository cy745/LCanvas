package com.lalilu.lcanvas.core

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.abs

expect fun Modifier.pointerScroll(onScroll: (PointerEvent) -> Unit): Modifier

@Composable
fun CanvasBox(
    state: CanvasState,
    modifier: Modifier = Modifier,
    onViewportChanged: ((Rect) -> Unit)? = null,
    gridCellWidth: Float = 10f,
    xAxisColor: Color = Color.Blue.copy(0.3f),
    yAxisColor: Color = Color.Red.copy(0.3f),
    gridColor: Color = Color(0xFF787878).copy(0.15f),
    content: @Composable CanvasItemsScope.() -> Unit
) {
    val itemsHost = remember { ItemsHostImpl(state) }

    Box(
        modifier = modifier
            .onSizeChanged { size: IntSize ->
                state.viewportSize = size
                onViewportChanged?.invoke(state.viewportLogicRect())
            }
            .pointerScroll(onScroll = { event ->
                val change = event.changes.firstOrNull() ?: return@pointerScroll
                val scaleDelta = 1f - (change.scrollDelta.y / 100f)
                state.anchorZoom(
                    focalRender = change.position,
                    scaleDelta = scaleDelta
                )
            })
            .pointerInput(state) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // 平移在渲染空间
                    state.pan(pan)
                    // 以手势质心为锚点缩放
                    state.anchorZoom(centroid, zoom)
                    onViewportChanged?.invoke(state.viewportLogicRect())
                }
            }
            .pointerInput(Unit) {
                val velocityTracker = VelocityTracker()
                coroutineScope {
                    detectDragGestures(
                        onDragEnd = {
                            val v = velocityTracker.calculateVelocity()
                            val velocity = Offset(v.x, v.y)
                            launch { state.fling(velocity) }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            launch {
                                state.cancelFling()
                                state.pan(dragAmount)
                            }
                        }
                    )
                }
            }
            .drawBgGrid(
                state = state,
                cellWidth = gridCellWidth,
                xBaseLineColor = xAxisColor,
                yBaseLineColor = yAxisColor,
                normalLineColor = gridColor,
            )
    ) {
        itemsHost.reset()
        content(CanvasScope(itemsHost))

        LazyLayout(
            itemProvider = { CanvasItemsProvider(state, itemsHost) },
            modifier = Modifier,
            measurePolicy = { constraints ->
                val viewportSize = IntSize(constraints.maxWidth, constraints.maxHeight)
                val viewportRect = Rect(
                    -state.overscanPx,
                    -state.overscanPx,
                    viewportSize.width.toFloat() + state.overscanPx,
                    viewportSize.height.toFloat() + state.overscanPx
                )
                state.viewportSize = viewportSize
                onViewportChanged?.invoke(state.viewportLogicRect())

                val t = state.transform()
                val visible = mutableListOf<VisibleItem>()
                for (i in itemsHost.states.indices) {
                    val layout = itemsHost.states[i].layout()
                    if (!layout.isVisible) continue

                    val logicRect = layout.rect
                    var renderRect = t.logicToRender(logicRect)
                    var measureStrategy: MeasureStrategy? = null
                    val scStrategy = itemsHost.states[i].scaleStrategy()

                    // 若元素的逻辑矩形和渲染矩形都为空，则需要处理用户测量策略
                    if (logicRect.isEmpty && renderRect.isEmpty) {
                        measureStrategy = itemsHost.states[i].measureStrategy()
                        renderRect = when (measureStrategy) {
                            is MeasureStrategy.WrapContent -> {
                                Rect(
                                    offset = renderRect.topLeft,
                                    size = Size(
                                        width = Float.MAX_VALUE,
                                        height = Float.MAX_VALUE
                                    )
                                )
                            }

                            is MeasureStrategy.Fixed -> {
                                Rect(
                                    offset = renderRect.topLeft,
                                    size = Size(
                                        width = measureStrategy.width.toPx(),
                                        height = measureStrategy.height.toPx()
                                    )
                                )
                            }
                        }
                    }

                    if (renderRect.overlaps(viewportRect)) {
                        visible.add(
                            VisibleItem(
                                index = i,
                                zIndex = layout.zIndex,
                                renderRect = renderRect,
                                logicRect = logicRect,
                                updateTime = layout.updateTime,
                                measureStrategy = measureStrategy,
                                scaleStrategy = scStrategy
                            )
                        )
                    }
                }

                val placeables: List<Pair<VisibleItem, Placeable>> = visible
                    .sortedBy { it.updateTime }
                    .map { v ->
                        val childConstraints =
                            if (v.renderRect.width == Float.MAX_VALUE || v.renderRect.height == Float.MAX_VALUE) {
                                constraints.copy(
                                    minWidth = 0,
                                    minHeight = 0,
                                    maxWidth = Int.MAX_VALUE,
                                    maxHeight = Int.MAX_VALUE
                                )
                            } else {
                                when (v.scaleStrategy) {
                                    is ScaleStrategy.ScaleInMeasure -> Constraints.fixed(
                                        v.renderRect.width.toInt().coerceAtLeast(0),
                                        v.renderRect.height.toInt().coerceAtLeast(0)
                                    )
                                    is ScaleStrategy.ScaleInRender -> Constraints.fixed(
                                        v.logicRect.width.toInt().coerceAtLeast(0),
                                        v.logicRect.height.toInt().coerceAtLeast(0)
                                    )
                                }
                            }

                        val measurables = compose(v.index)
                        val p = measurables.first().measure(childConstraints)

                        // 处理用户测量策略
                        if (v.measureStrategy is MeasureStrategy.WrapContent) {
                            val newLogicRect = t.renderToLogic(
                                Rect(
                                    offset = v.renderRect.topLeft,
                                    size = Size(
                                        width = p.measuredWidth.toFloat(),
                                        height = p.measuredHeight.toFloat()
                                    )
                                )
                            )
                            v.measureStrategy.onMeasured(newLogicRect)
                        }

                        Pair(v, p)
                    }

                layout(viewportSize.width, viewportSize.height) {
                    placeables.forEach { (v, p) ->
                        p.place(
                            x = v.renderRect.left.toInt(),
                            y = v.renderRect.top.toInt(),
                            zIndex = v.zIndex
                        )
                    }
                }
            }
        )
    }
}

fun Modifier.drawBgGrid(
    state: CanvasState,
    cellWidth: Float = 100f,
    xBaseLineColor: Color = Color.Blue,
    yBaseLineColor: Color = Color.Red,
    normalLineColor: Color = Color(0xFFEEEEEE),
) = drawBehind {
    val t = state.transform()
    val logic = state.viewportLogicRect()
    val startX = floor(logic.left / cellWidth) * cellWidth
    val endX = ceil(logic.right / cellWidth) * cellWidth
    val startY = floor(logic.top / cellWidth) * cellWidth
    val endY = ceil(logic.bottom / cellWidth) * cellWidth
    var x = startX
    while (x <= endX) {
        val p1 = t.logicToRender(Offset(x, startY))
        val p2 = t.logicToRender(Offset(x, endY))
        val isMajor = abs(x - floor(x / 100f) * 100f) < 1e-3f
        val stroke = when {
            isMajor && state.scale < 0.5f -> 1f
            isMajor -> 2f
            state.scale < 0.5f -> 0f
            else -> 1f
        }
        if (stroke > 0f) drawLine(normalLineColor, p1, p2, stroke)
        x += cellWidth
    }
    var y = startY
    while (y <= endY) {
        val p1 = t.logicToRender(Offset(startX, y))
        val p2 = t.logicToRender(Offset(endX, y))
        val isMajor = abs(y - floor(y / 100f) * 100f) < 1e-3f
        val stroke = when {
            isMajor && state.scale < 0.5f -> 1f
            isMajor -> 2f
            state.scale < 0.5f -> 0f
            else -> 1f
        }
        if (stroke > 0f) drawLine(normalLineColor, p1, p2, stroke)
        y += cellWidth
    }
    if (logic.left <= 0f && logic.right >= 0f) {
        val p1 = t.logicToRender(Offset(0f, startY))
        val p2 = t.logicToRender(Offset(0f, endY))
        drawLine(xBaseLineColor, p1, p2, 3f)
    }
    if (logic.top <= 0f && logic.bottom >= 0f) {
        val p1 = t.logicToRender(Offset(startX, 0f))
        val p2 = t.logicToRender(Offset(endX, 0f))
        drawLine(yBaseLineColor, p1, p2, 3f)
    }
}

@Immutable
data class CanvasItemState(
    val layout: () -> CanvasItemLayout,
    val measureStrategy: () -> MeasureStrategy,
    val scaleStrategy: () -> ScaleStrategy,
    val content: @Composable CanvasChildScope.() -> Unit,
    val key: Any
)

private class ItemsHostImpl(
    private val state: CanvasState
) : CanvasItemsScope {
    val states = mutableListOf<CanvasItemState>()

    fun reset() {
        states.clear()
    }

    override fun items(
        count: Int,
        key: ((Int) -> Any)?,
        layoutInfo: (Int) -> CanvasItemLayout,
        measureStrategy: (Int) -> MeasureStrategy,
        scaleStrategy: (Int) -> ScaleStrategy,
        itemContent: @Composable CanvasChildScope.(index: Int) -> Unit
    ) {
        for (i in 0 until count) {
            states.add(
                CanvasItemState(
                    layout = { layoutInfo(i) },
                    content = { itemContent(i) },
                    measureStrategy = { measureStrategy(i) },
                    scaleStrategy = { scaleStrategy(i) },
                    key = key?.invoke(i) ?: i
                )
            )
        }
    }

    override fun <T : Any> items(
        items: List<T>,
        key: ((T) -> Any)?,
        layoutInfo: (T) -> CanvasItemLayout,
        measureStrategy: (T) -> MeasureStrategy,
        scaleStrategy: (T) -> ScaleStrategy,
        itemContent: @Composable (CanvasChildScope.(item: T) -> Unit)
    ) {
        states.addAll(items.map {
            CanvasItemState(
                layout = { layoutInfo(it) },
                content = { itemContent(it) },
                measureStrategy = { measureStrategy(it) },
                scaleStrategy = { scaleStrategy(it) },
                key = key?.invoke(it) ?: it
            )
        })
    }
}

private data class VisibleItem(
    val index: Int,
    val zIndex: Float,
    val updateTime: Long,
    val renderRect: Rect,
    val logicRect: Rect,
    val measureStrategy: MeasureStrategy? = null,
    val scaleStrategy: ScaleStrategy = ScaleStrategy.ScaleInMeasure,
)

private class CanvasItemsProvider(
    private val state: CanvasState,
    private val host: ItemsHostImpl
) : LazyLayoutItemProvider {
    override val itemCount: Int get() = host.states.size
    override fun getKey(index: Int): Any = host.states[index].key

    @Composable
    override fun Item(index: Int, key: Any) {
        val childScope = CanvasChildScope(
            transform = state.transform(),
            scale = state.scale,
            scaleStrategy = host.states[index].scaleStrategy(),
        )

        var itemState = host.states[index]
        if (itemState.key != key) {
            itemState = host.states.firstOrNull { it.key == key }
                ?: itemState
        }

        itemState.content.invoke(childScope)
    }
}
