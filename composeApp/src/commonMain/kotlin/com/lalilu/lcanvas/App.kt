package com.lalilu.lcanvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.github.panpf.sketch.AsyncImage
import com.lalilu.lcanvas.core.CanvasBox
import com.lalilu.lcanvas.core.FloatItem
import com.lalilu.lcanvas.core.MeasureStrategy
import com.lalilu.lcanvas.core.rememberCanvasState
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        Column(
            modifier = Modifier
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
    val list = remember {
        listOf(
            FloatItem(key = "hello world") {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Inside,
                    contentDescription = "",
                    uri = "https://www.dmoe.cc/random.php"
                )
            },
            FloatItem(key = "hello world2") {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Inside,
                    contentDescription = "",
                    uri = "https://www.dmoe.cc/random.php"
                )
            }
        )
    }

    CanvasBox(state = state, modifier = Modifier.fillMaxSize()) {
        items(
            items = list,
            key = { it.key },
            layoutInfo = { it.layout.value },
            measureStrategy = { item -> MeasureStrategy.WrapContent { rect -> item.updateLayout { copy(rect = rect) } } },
            itemContent = { with(it) { Content() } }
        )
    }
}