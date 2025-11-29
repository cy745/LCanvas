package com.lalilu.lcanvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.github.panpf.sketch.AsyncImage
import com.lalilu.lcanvas.core.*
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
    val floatItemState = rememberFloatItemState()

    val list = remember {
        listOf(
            FloatItem(
                key = "hello world",
                state = floatItemState
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    contentDescription = "",
                    uri = "https://www.dmoe.cc/random.php"
                )
            },
            FloatItem(
                key = "hello world2",
                state = floatItemState
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
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
            onUpdateLayoutInfo = { item, layout -> item.updateLayout { layout } },
            scaleStrategy = { ScaleStrategy.ScaleInRender },
            itemContent = { with(it) { Content() } }
        )
    }
}