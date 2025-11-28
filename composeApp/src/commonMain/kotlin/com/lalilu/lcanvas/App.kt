package com.lalilu.lcanvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
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
        val count = 100
        val cols = 10
        val rows = 10
        val cellW = 256f
        val cellH = 256f
        val itemW = 128f
        val itemH = 64f

        fun prand(n: Int): Float {
            val v = ((n * 1103515245L + 12345L) and 0x7fffffff).toDouble()
            return (v / Int.MAX_VALUE.toDouble()).toFloat()
        }

        items(
            count = count,
            key = { i -> i },
            layoutInfo = { i ->
                val col = i % cols
                val row = i / cols
                val baseX = col * cellW
                val baseY = row * cellH
                val jitterX = (cellW - itemW) * prand(i * 2 + 7)
                val jitterY = (cellH - itemH) * prand(i * 2 + 13)
                val x = baseX + jitterX
                val y = baseY + jitterY
                CanvasItemLayout(x = x, y = y, width = itemW, height = itemH)
            }
        ) { index ->
            Cell(index = index, scope = this)
        }
    }
}

@Composable
private fun Cell(index: Int, scope: CanvasChildScope) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .border(
                width = 1.dp,
                color = Color.Blue
            )
    ) {
        androidx.compose.material3.Text(
            text = "#${index}",
            color = Color(0xFF0D47A1),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}
