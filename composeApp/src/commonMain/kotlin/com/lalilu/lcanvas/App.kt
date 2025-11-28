package com.lalilu.lcanvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.lalilu.lcanvas.core.CanvasBox
import com.lalilu.lcanvas.core.CanvasChildScope
import com.lalilu.lcanvas.core.CanvasItemLayout
import com.lalilu.lcanvas.core.rememberCanvasState
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DemoCanvas()
        }
    }
}

@Composable
private fun DemoCanvas() {
    val state = rememberCanvasState(overscanPx = 512f)
    CanvasBox(state = state, modifier = Modifier.fillMaxSize()) {
        val columns = 100
        val rows = 60
        items(count = columns * rows, layoutInfo = { i ->
            val x = (i % columns) * 128f
            val y = (i / columns) * 64f
            CanvasItemLayout(x = x, y = y, width = 128f, height = 64f)
        }) { index ->
            Cell(index = index, scope = this)
        }
    }
}

@Composable
private fun Cell(index: Int, scope: CanvasChildScope) {
    androidx.compose.foundation.layout.Box {
        androidx.compose.material3.Text(
            text = "#${index}",
            color = Color(0xFF0D47A1),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}
