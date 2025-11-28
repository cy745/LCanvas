package com.lalilu.lcanvas.core

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize

expect fun Modifier.pointerScroll(onScroll: (PointerEvent) -> Unit): Modifier

@Composable
fun CanvasBox(
    state: CanvasState,
    modifier: Modifier = Modifier,
    onViewportChanged: ((Rect) -> Unit)? = null,
    content: @Composable CanvasScope.() -> Unit
) {
    val itemsHost = remember { ItemsHostImpl(state) }
    val lastText = remember { mutableStateOf("") }

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
    ) {
        BasicText(lastText.value)

        itemsHost.reset()
        content(CanvasScope(itemsHost))

        @OptIn(ExperimentalFoundationApi::class)
        LazyLayout(
            itemProvider = { CanvasItemsProvider(state, itemsHost) },
            modifier = Modifier,
            measurePolicy = { constraints ->
                val viewportSize = IntSize(constraints.maxWidth, constraints.maxHeight)
                state.viewportSize = viewportSize
                onViewportChanged?.invoke(state.viewportLogicRect())

                val t = state.transform()
                val overscanLogic = (state.overscanPx / state.scale)
                val logicViewport = state.viewportLogicRect()
                val expandedLogicViewport = Rect(
                    logicViewport.left - overscanLogic,
                    logicViewport.top - overscanLogic,
                    logicViewport.right + overscanLogic,
                    logicViewport.bottom + overscanLogic
                )

                val candidateIndices = itemsHost.index.query(expandedLogicViewport)
                val visible = mutableListOf<VisibleItem>()
                for (i in candidateIndices) {
                    val it = itemsHost.items[i]
                    val logicRect = Rect(it.x, it.y, it.x + it.width, it.y + it.height)
                    val renderRect = t.logicToRender(logicRect)
                    visible.add(VisibleItem(i, renderRect, logicRect))
                }

                val placeables: List<Pair<VisibleItem, androidx.compose.ui.layout.Placeable>> = visible.map { v ->
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

private class ItemsHostImpl(
    private val state: CanvasState
) : CanvasItemsHost {
    val items = mutableListOf<CanvasItemLayout>()
    val contents = mutableListOf<@Composable CanvasChildScope.(Int) -> Unit>()
    val keys = mutableListOf<Any>()
    val index = SpatialIndex(cellSize = 256f)

    fun reset() {
        items.clear()
        contents.clear()
        keys.clear()
        index.clear()
    }

    override fun registerIntItems(
        count: Int,
        key: ((Int) -> Any)?,
        layoutInfo: (Int) -> CanvasItemLayout,
        itemContent: @Composable CanvasChildScope.(index: Int) -> Unit
    ) {
        for (i in 0 until count) {
            items.add(layoutInfo(i))
            contents.add(itemContent)
            keys.add(key?.invoke(i) ?: i)
            val it = items.last()
            val logicRect = Rect(it.x, it.y, it.x + it.width, it.y + it.height)
            index.add(items.size - 1, logicRect)
        }
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
    override val itemCount: Int get() = host.items.size
    override fun getKey(index: Int): Any = host.keys[index]

    @Composable
    override fun Item(index: Int, key: Any) {
        val it = host.items[index]
        val t = state.transform()
        val logicRect = Rect(it.x, it.y, it.x + it.width, it.y + it.height)
        val renderRect = t.logicToRender(logicRect)
        val childScope = CanvasChildScope(
            transform = t,
            scale = state.scale,
            renderRect = renderRect,
            logicRect = logicRect
        )
        host.contents[index].invoke(childScope, index)
    }
}
