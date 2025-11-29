package com.lalilu.lcanvas.core

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
data class FloatItem(
    val key: String,
    val layout: MutableState<CanvasItemLayout> = mutableStateOf(CanvasItemLayout.Default),
    private val content: @Composable BoxScope.(FloatItem) -> Unit = {}
) {
    fun updateLayout(block: CanvasItemLayout.() -> CanvasItemLayout) {
        layout.value = layout.value.block()
    }

    @Composable
    fun CanvasChildScope.Content(modifier: Modifier = Modifier) {
        val dragging = remember { mutableStateOf(false) }
        val draggable = rememberDraggable2DState { offset ->
            updateLayout { copy(rect = rect.translate(offset)) }
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .border(width = 1.dp, color = Color.Red)
                .draggable2D(
                    state = draggable,
                    onDragStarted = {
                        dragging.value = true
                        updateLayout { copy(updateTime = Clock.System.now().toEpochMilliseconds()) }
                    },
                    onDragStopped = { dragging.value = false }
                )
        ) {
            content(this@FloatItem)
        }
    }
}