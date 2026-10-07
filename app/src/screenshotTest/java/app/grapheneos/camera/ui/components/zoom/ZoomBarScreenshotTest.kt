package app.grapheneos.camera.ui.components.zoom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import com.android.tools.screenshot.PreviewTest

private val RANGE = 0.5f..8f
private val STOPS = listOf(0.5f, 1f, 2f, 8f)

@PreviewTest
@PreviewLightDark
@Composable
private fun ZoomBarValues() {
    CameraPreviewColumn {
        ZoomBarColumn()
    }
}

@PreviewTest
@Preview(
    name = "Persian",
    locale = "fa",
)
@Composable
private fun ZoomBarNumerals() {
    CameraPreviewColumn {
        ZoomBarColumn()
    }
}

@Composable
private fun ZoomBarColumn() {
    Column(
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        listOf(0.5f, 0.6f, 1f, 2.7f, 8f).forEach { value ->
            ZoomBar(
                value = value,
                onValueChange = {},
                valueRange = RANGE,
                stops = STOPS,
            )
        }
        ZoomBar(
            value = 5f,
            onValueChange = {},
            valueRange = 0.5f..10f,
            stops = listOf(0.5f, 1f, 2f, 5f, 10f),
        )
        ZoomBar(
            value = 1f,
            onValueChange = {},
            valueRange = RANGE,
            stops = STOPS,
            enabled = false,
        )
    }
}
