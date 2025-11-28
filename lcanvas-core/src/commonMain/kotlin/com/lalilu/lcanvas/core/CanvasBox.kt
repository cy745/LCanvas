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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

expect fun Modifier.pointerScroll(onScroll: (PointerEvent) -> Unit): Modifier

@Composable
fun CanvasBox(
    state: CanvasState,
    modifier: Modifier = Modifier,
    onViewportChanged: ((Rect) -> Unit)? = null,
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
                    val logicRect = itemsHost.states[i].layout()
                    val renderRect = t.logicToRender(logicRect)

                    if (renderRect.overlaps(viewportRect)) {
                        visible.add(VisibleItem(i, renderRect, logicRect))
                    }
                }

                val placeables: List<Pair<VisibleItem, Placeable>> = visible.map { v ->
                    val childConstraints = Constraints.fixed(
                        v.renderRect.width.toInt().coerceAtLeast(0),
                        v.renderRect.height.toInt().coerceAtLeast(0)
                    )
                    val measurables = compose(v.index)
                    val p = measurables.first().measure(childConstraints)
                    Pair(v, p)
                }

                layout(viewportSize.width, viewportSize.height) {
                    placeables.forEach { (v, p) ->
                        p.place(v.renderRect.left.toInt(), v.renderRect.top.toInt())
                    }
                }
            }
        )
    }
}

@Immutable
data class CanvasItemState(
    val layout: () -> Rect,
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
        layoutInfo: (Int) -> Rect,
        measureStrategy: (Int) -> MeasureStrategy,
        itemContent: @Composable CanvasChildScope.(index: Int) -> Unit
    ) {
        for (i in 0 until count) {
            states.add(
                CanvasItemState(
                    layout = { layoutInfo(i) },
                    content = { itemContent(i) },
                    key = key?.invoke(i) ?: i
                )
            )
        }
    }

    override fun <T : Any> items(
        items: List<T>,
        key: ((T) -> Any)?,
        layoutInfo: (T) -> Rect,
        measureStrategy: (T) -> MeasureStrategy,
        itemContent: @Composable (CanvasChildScope.(item: T) -> Unit)
    ) {
        states.addAll(items.map {
            CanvasItemState(
                layout = { layoutInfo(it) },
                content = { itemContent(it) },
                key = key?.invoke(it) ?: it
            )
        })
    }
}

private data class VisibleItem(
    val index: Int,
    val renderRect: Rect,
    val logicRect: Rect
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
        )

        var itemState = host.states[index]
        if (itemState.key != key) {
            itemState = host.states.firstOrNull { it.key == key }
                ?: itemState
        }

        itemState.content.invoke(childScope)
    }
}
