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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
                    val itemState = itemsHost.states[i]
                    val layout = itemState.layout()
                    if (!layout.isVisible) continue

                    val logicRect = layout.rect
                    var renderRect = t.logicToRender(logicRect)
                    val scStrategy = itemState.scaleStrategy()

                    // 若元素未被测量，则使用measureStrategy，进行处理
                    if (!layout.isMeasured) {
                        renderRect = when (val measureStrategy = itemState.measureStrategy()) {
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
                                scaleStrategy = scStrategy,
                                isMeasured = layout.isMeasured
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
                        val itemState = itemsHost.states[v.index]

                        // 若元素先前未被测量，则此时把元素的测量结果返回给CanvasItem
                        if (!v.isMeasured) {
                            val newLogicRect = t.renderToLogic(
                                Rect(
                                    offset = v.renderRect.topLeft,
                                    size = Size(
                                        width = p.measuredWidth.toFloat(),
                                        height = p.measuredHeight.toFloat()
                                    )
                                )
                            )

                            if (!newLogicRect.isEmpty) {
                                val newLayout = itemState.layout().copy(
                                    rect = newLogicRect,
                                    isMeasured = true
                                )
                                itemState.onUpdateLayout(newLayout)
                            }
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
    val onUpdateLayout: (CanvasItemLayout) -> Unit,
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
        onUpdateLayoutInfo: (Int, CanvasItemLayout) -> Unit,
        measureStrategy: (Int) -> MeasureStrategy,
        scaleStrategy: (Int) -> ScaleStrategy,
        itemContent: @Composable CanvasChildScope.(index: Int) -> Unit
    ) {
        for (i in 0 until count) {
            states.add(
                CanvasItemState(
                    layout = { layoutInfo(i) },
                    onUpdateLayout = { onUpdateLayoutInfo(i, it) },
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
        onUpdateLayoutInfo: (T, CanvasItemLayout) -> Unit,
        measureStrategy: (T) -> MeasureStrategy,
        scaleStrategy: (T) -> ScaleStrategy,
        itemContent: @Composable (CanvasChildScope.(item: T) -> Unit)
    ) {
        states.addAll(items.map { item ->
            CanvasItemState(
                layout = { layoutInfo(item) },
                onUpdateLayout = { onUpdateLayoutInfo(item, it) },
                content = { itemContent(item) },
                measureStrategy = { measureStrategy(item) },
                scaleStrategy = { scaleStrategy(item) },
                key = key?.invoke(item) ?: item
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
    val isMeasured: Boolean,
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
        var itemState = host.states[index]
        if (itemState.key != key) {
            itemState = host.states.firstOrNull { it.key == key }
                ?: itemState
        }

        val childScope = CanvasChildScope(
            transform = state.transform(),
            scale = state.scale,
            scaleStrategy = itemState.scaleStrategy()
        )

        Box(
            modifier = Modifier.graphicsLayer {
                if (itemState.scaleStrategy() == ScaleStrategy.ScaleInRender) {
                    transformOrigin = TransformOrigin.Center.copy(0f, 0f)
                    scaleX = state.scale
                    scaleY = state.scale
                }
            }, content = { itemState.content.invoke(childScope) }
        )
    }
}
