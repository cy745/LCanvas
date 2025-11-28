package com.lalilu.lcanvas.core

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent

actual fun Modifier.pointerScroll(onScroll: (PointerEvent) -> Unit): Modifier = this
