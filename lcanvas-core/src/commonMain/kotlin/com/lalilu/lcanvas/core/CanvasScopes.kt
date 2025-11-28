package com.lalilu.lcanvas.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect

@Immutable
data class CanvasItemLayout(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val lockX: Boolean = false,
    val lockY: Boolean = false
)

@Stable
class CanvasChildScope(
    val transform: CanvasTransform,
    val scale: Float,
    val renderRect: Rect,
    val logicRect: Rect
)

@Stable
class CanvasScope internal constructor(
    private val impl: CanvasItemsHost
) {
    fun items(
        count: Int,
        key: ((Int) -> Any)? = null,
        layoutInfo: (Int) -> CanvasItemLayout,
        itemContent: @Composable CanvasChildScope.(index: Int) -> Unit
    ) {
        impl.registerIntItems(count, key, layoutInfo, itemContent)
    }
}

internal interface CanvasItemsHost {
    fun registerIntItems(
        count: Int,
        key: ((Int) -> Any)? = null,
        layoutInfo: (Int) -> CanvasItemLayout,
        itemContent: @Composable CanvasChildScope.(index: Int) -> Unit
    )
}
