package app.grapheneos.camera.ui.components.modeselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import com.android.tools.screenshot.PreviewTest

private val LABELS = listOf(
    "Long Exposure",
    "Portrait",
    "Photo",
    "Night Sight",
    "Panorama",
)

@PreviewTest
@PreviewLightDark
@Composable
private fun ModeSelectorStates() {
    CameraPreviewColumn {
        ModeSelectorColumn()
    }
}

@PreviewTest
@Preview(
    name = "Arabic",
    locale = "ar-rEG",
)
@Preview(
    name = "LargeFont",
    fontScale = 2f,
)
@Composable
private fun ModeSelectorLayouts() {
    CameraPreviewColumn {
        ModeSelectorColumn()
    }
}

@Composable
private fun ModeSelectorColumn() {
    Column(
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        listOf(0, 2, LABELS.lastIndex).forEach { selectedIndex ->
            ModeSelector(
                labels = LABELS,
                selectedIndex = selectedIndex,
                onModeSelected = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ModeSelector(
            labels = LABELS,
            selectedIndex = 2,
            onModeSelected = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
        )
    }
}
