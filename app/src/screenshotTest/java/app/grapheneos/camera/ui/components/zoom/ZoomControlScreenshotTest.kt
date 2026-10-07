package app.grapheneos.camera.ui.components.zoom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun ZoomControlStates() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            ZoomControlState(expanded = false)
            ZoomControlState(expanded = true)
            ZoomControlState(
                expanded = false,
                enabled = false,
            )
        }
    }
}

@Composable
private fun ZoomControlState(
    expanded: Boolean,
    enabled: Boolean = true,
) {
    ZoomControl(
        value = 1.4f,
        onValueChange = {},
        valueRange = 0.5f..8f,
        stops = listOf(0.5f, 1f, 2f),
        valueSuffix = "×",
        expanded = expanded,
        onExpand = {},
        onStopClick = {},
        expandLabel = "Adjust zoom",
        enabled = enabled,
    )
}
