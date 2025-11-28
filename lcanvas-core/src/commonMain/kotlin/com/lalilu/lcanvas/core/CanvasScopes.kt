package com.lalilu.lcanvas.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp

sealed class MeasureStrategy {
    data class Fixed(val width: Dp, val height: Dp) : MeasureStrategy()
    data object WrapContent : MeasureStrategy()
}

@Stable
data class CanvasChildScope(
    val transform: CanvasTransform,
    val scale: Float,
)

interface CanvasItemsScope {
    fun items(
        count: Int,
        key: ((Int) -> Any)? = null,
        layoutInfo: (Int) -> Rect = { Rect(Offset.Zero, Size(100f, 100f)) },
        measureStrategy: (Int) -> MeasureStrategy = { MeasureStrategy.WrapContent },
        itemContent: @Composable CanvasChildScope.(index: Int) -> Unit
    )

    fun <T : Any> items(
        items: List<T>,
        key: ((T) -> Any)? = null,
        layoutInfo: (T) -> Rect = { Rect(Offset.Zero, Size(100f, 100f)) },
        measureStrategy: (T) -> MeasureStrategy = { MeasureStrategy.WrapContent },
        itemContent: @Composable CanvasChildScope.(item: T) -> Unit
    )
}

@Stable
class CanvasScope internal constructor(
    private val impl: CanvasItemsScope
) : CanvasItemsScope by impl