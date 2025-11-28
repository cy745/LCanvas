package com.lalilu.lcanvas.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp

sealed class MeasureStrategy {
    data class Fixed(val width: Dp, val height: Dp) : MeasureStrategy()
    data object WrapContent : MeasureStrategy()
}

@Immutable
data class CanvasItemLayout(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

@Stable
data class CanvasChildScope(
    val transform: CanvasTransform,
    val scale: Float,
)

interface CanvasItemsScope {
    fun items(
        count: Int,
        key: ((Int) -> Any)? = null,
        layoutInfo: (Int) -> CanvasItemLayout = { CanvasItemLayout(0f, 0f, 0f, 0f) },
        measureStrategy: (Int) -> MeasureStrategy = { MeasureStrategy.WrapContent },
        itemContent: @Composable CanvasChildScope.(index: Int) -> Unit
    )
}

@Stable
class CanvasScope internal constructor(
    private val impl: CanvasItemsScope
) : CanvasItemsScope by impl