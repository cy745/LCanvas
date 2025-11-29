package com.lalilu.lcanvas.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp

sealed class MeasureStrategy {
    data class Fixed(val width: Dp, val height: Dp) : MeasureStrategy()
    data class WrapContent(val onMeasured: (logicRect: Rect) -> Unit) : MeasureStrategy()
}

sealed class ScaleStrategy {
    data object ScaleInMeasure : ScaleStrategy()
    data object ScaleInRender : ScaleStrategy()
}

@Stable
data class CanvasItemLayout(
    val zIndex: Float = 0f,
    val rect: Rect = Rect.Zero,
    val updateTime: Long = 0,
    val isVisible: Boolean = true
) {
    companion object {
        val Default = CanvasItemLayout()
    }
}

@Stable
data class CanvasChildScope(
    val transform: CanvasTransform,
    val scale: Float,
    val scaleStrategy: ScaleStrategy,
)

interface CanvasItemsScope {
    fun items(
        count: Int,
        key: ((Int) -> Any)? = null,
        layoutInfo: (Int) -> CanvasItemLayout = { CanvasItemLayout.Default },
        measureStrategy: (Int) -> MeasureStrategy = { MeasureStrategy.WrapContent {} },
        scaleStrategy: (Int) -> ScaleStrategy = { ScaleStrategy.ScaleInMeasure },
        itemContent: @Composable CanvasChildScope.(index: Int) -> Unit
    )

    fun <T : Any> items(
        items: List<T>,
        key: ((T) -> Any)? = null,
        layoutInfo: (T) -> CanvasItemLayout = { CanvasItemLayout.Default },
        measureStrategy: (T) -> MeasureStrategy = { MeasureStrategy.WrapContent {} },
        scaleStrategy: (T) -> ScaleStrategy = { ScaleStrategy.ScaleInMeasure },
        itemContent: @Composable CanvasChildScope.(item: T) -> Unit
    )
}

@Stable
class CanvasScope internal constructor(
    private val impl: CanvasItemsScope
) : CanvasItemsScope by impl
