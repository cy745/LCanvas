package com.lalilu.lcanvas.core

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

@Stable
class CanvasState(
    initialScale: Float = 1f,
    initialTranslation: Offset = Offset.Zero,
    overscanPx: Float = 256f
) {
    var scale by mutableFloatStateOf(initialScale)
        private set

    var translation by mutableStateOf(initialTranslation)
        private set

    var viewportSize by mutableStateOf(IntSize(0, 0))
        internal set

    var overscanPx by mutableFloatStateOf(overscanPx)
        internal set

    fun transform(): CanvasTransform = CanvasTransform(scale, translation)

    fun viewportLogicRect(): Rect = transform().viewportLogicRect(viewportSize)

    fun updateScale(value: Float) {
        scale = value.coerceAtLeast(0.1f)
    }

    fun updateTranslation(value: Offset) {
        translation = value
    }

    fun pan(deltaRender: Offset) {
        translation += deltaRender
    }

    /**
     * 以渲染空间中的焦点为锚进行缩放，保证该焦点对应的逻辑坐标在缩放前后保持不变
     */
    fun anchorZoom(focalRender: Offset, scaleDelta: Float) {
        val oldScale = scale
        val newScale = (oldScale * scaleDelta).coerceAtLeast(0.1f)
        if (newScale == oldScale) return

        // 计算焦点在逻辑空间中的坐标
        val logicAtFocal = transform().renderToLogic(focalRender)
        // 使缩放后该逻辑点仍落在同一渲染点： focalRender = logicAtFocal * newScale + newTranslation
        // => newTranslation = focalRender - logicAtFocal * newScale
        scale = newScale
        translation = focalRender - Offset(logicAtFocal.x * newScale, logicAtFocal.y * newScale)
    }
}

@Composable
fun rememberCanvasState(
    initialScale: Float = 1f,
    initialTranslation: Offset = Offset.Zero,
    overscanPx: Float = 256f
): CanvasState = remember { CanvasState(initialScale, initialTranslation, overscanPx) }
