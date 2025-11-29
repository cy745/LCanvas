package com.lalilu.lcanvas.core

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class FloatItemState(
    val selectedKey: String? = null,
)

@Composable
fun rememberFloatItemState(): MutableState<FloatItemState> = remember {
    mutableStateOf(FloatItemState())
}

@OptIn(ExperimentalTime::class)
data class FloatItem(
    val key: String,
    val state: MutableState<FloatItemState>,
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
            updateLayout {
                val targetOffset = when (this@Content.scaleStrategy) {
                    ScaleStrategy.ScaleInMeasure -> offset / scale
                    ScaleStrategy.ScaleInRender -> offset
                }
                copy(rect = rect.translate(targetOffset))
            }
        }
        val isSelected = remember {
            derivedStateOf { key == state.value.selectedKey }
        }
        val elevationAnimation = animateDpAsState(
            targetValue = if (dragging.value) 20.dp else 5.dp,
            animationSpec = spring()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(if (isSelected.value) 2.dp else 0.dp, Color.Black.copy(0.5f))
                .padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                elevation = elevationAnimation.value,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(0.5f.dp, Color.Black.copy(0.05f))
            ) {
                Box(
                    modifier = modifier
                        .sizeIn(100.dp)
                        .aspectRatio(16f / 9f)
                        .fillMaxSize()
                        .draggable2D(
                            state = draggable,
                            onDragStarted = {
                                dragging.value = true
                                state.value = state.value.copy(selectedKey = key)
                                updateLayout { copy(updateTime = Clock.System.now().toEpochMilliseconds()) }
                            },
                            onDragStopped = { dragging.value = false }
                        ),
                    content = {
                        content(this@FloatItem)
                    }
                )
            }
        }
    }
}