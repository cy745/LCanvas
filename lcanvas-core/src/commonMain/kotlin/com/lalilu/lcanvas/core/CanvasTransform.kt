package com.lalilu.lcanvas.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

class CanvasTransform(
    val scale: Float,
    val translation: Offset
) {
    fun logicToRender(offset: Offset): Offset = offset * scale + translation
    fun renderToLogic(offset: Offset): Offset = (offset - translation) / scale

    fun logicToRender(rect: Rect): Rect = Rect(
        logicToRender(rect.topLeft),
        logicToRender(rect.bottomRight)
    )

    fun renderToLogic(rect: Rect): Rect = Rect(
        renderToLogic(rect.topLeft),
        renderToLogic(rect.bottomRight)
    )

    fun viewportLogicRect(viewportSize: IntSize): Rect = renderToLogic(
        Rect(0f, 0f, viewportSize.width.toFloat(), viewportSize.height.toFloat())
    )
}

private operator fun Offset.times(scale: Float): Offset = Offset(x * scale, y * scale)
private operator fun Offset.div(scale: Float): Offset = Offset(x / scale, y / scale)
