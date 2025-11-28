package com.lalilu.lcanvas.core

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent


@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.pointerScroll(onScroll: (PointerEvent) -> Unit) = this.onPointerEvent(PointerEventType.Scroll) {
    onScroll(it)
}