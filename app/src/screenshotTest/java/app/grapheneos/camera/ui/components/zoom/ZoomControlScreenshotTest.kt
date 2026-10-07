package app.grapheneos.camera.ui.components.zoom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import com.android.tools.screenshot.PreviewTest

private val THREE_STOPS = listOf(0.5f, 1f, 2f)
private val THREE_STOPS_RANGE = 0.5f..8f

@PreviewTest
@PreviewLightDark
@Composable
private fun ZoomControlStates() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            ThreeStopZoomControl(expanded = false)
            ThreeStopZoomControl(expanded = true)
            ThreeStopZoomControl(
                expanded = false,
                enabled = false,
            )
        }
    }
}

@Composable
private fun ThreeStopZoomControl(
    expanded: Boolean,
    enabled: Boolean = true,
) {
    ZoomControl(
        value = 1.4f,
        onValueChange = {},
        valueRange = THREE_STOPS_RANGE,
        stops = THREE_STOPS,
        valueSuffix = "×",
        expanded = expanded,
        onExpand = {},
        onStopClick = {},
        expandLabel = "Adjust zoom",
        enabled = enabled,
    )
}
