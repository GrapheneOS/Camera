package app.grapheneos.camera.ui.components.zoom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import com.android.tools.screenshot.PreviewTest

private val THREE_STOPS = listOf(0.5f, 1f, 2f)
private val FIVE_STOPS = listOf(0.5f, 1f, 2f, 5f, 10f)

@PreviewTest
@PreviewLightDark
@Composable
private fun ZoomStopsValues() {
    CameraPreviewColumn {
        ZoomStopsColumn()
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
private fun ZoomStopsLayouts() {
    CameraPreviewColumn {
        ZoomStopsColumn()
    }
}

@Composable
private fun ZoomStopsColumn() {
    Column(
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        listOf(0.6f, 1f, 4.5f).forEach { value ->
            ZoomStops(
                value = value,
                stops = THREE_STOPS,
                valueSuffix = "×",
                onStopClick = {},
            )
        }
        ZoomStops(
            value = 9.9f,
            stops = FIVE_STOPS,
            valueSuffix = "×",
            onStopClick = {},
        )
        ZoomStops(
            value = 1f,
            stops = THREE_STOPS,
            valueSuffix = "×",
            onStopClick = {},
            enabled = false,
        )
    }
}
