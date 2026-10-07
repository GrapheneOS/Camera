package app.grapheneos.camera.ui.components.zoom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import com.android.tools.screenshot.PreviewTest

private val FOUR_STOPS = listOf(0.5f, 1f, 2f, 8f)
private val FOUR_STOPS_RANGE = 0.5f..8f
private val FIVE_STOPS = listOf(0.5f, 1f, 2f, 5f, 10f)
private val FIVE_STOPS_RANGE = 0.5f..10f

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
@Preview(
    name = "LargeFont",
    fontScale = 2f,
)
@Composable
private fun ZoomBarLayouts() {
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
                valueRange = FOUR_STOPS_RANGE,
                stops = FOUR_STOPS,
            )
        }
        ZoomBar(
            value = 5f,
            onValueChange = {},
            valueRange = FIVE_STOPS_RANGE,
            stops = FIVE_STOPS,
        )
        ZoomBar(
            value = 1f,
            onValueChange = {},
            valueRange = FOUR_STOPS_RANGE,
            stops = FOUR_STOPS,
            enabled = false,
        )
    }
}
