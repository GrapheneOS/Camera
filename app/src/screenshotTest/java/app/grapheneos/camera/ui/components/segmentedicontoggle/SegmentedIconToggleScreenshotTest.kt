package app.grapheneos.camera.ui.components.segmentedicontoggle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.segmentedicontoggle.model.SegmentedIconToggleOption
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_PHOTO_CAMERA_ICON
import app.grapheneos.camera.ui.core.PREVIEW_VIDEOCAM_ICON
import com.android.tools.screenshot.PreviewTest

private val OPTIONS = listOf(
    SegmentedIconToggleOption(
        icon = PREVIEW_PHOTO_CAMERA_ICON,
        contentDescription = "Photo",
    ),
    SegmentedIconToggleOption(
        icon = PREVIEW_VIDEOCAM_ICON,
        contentDescription = "Video",
    ),
)

@PreviewTest
@PreviewLightDark
@Composable
private fun SegmentedIconToggleStates() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            OPTIONS.indices.forEach { selectedIndex ->
                SegmentedIconToggle(
                    options = OPTIONS,
                    selectedIndex = selectedIndex,
                    onOptionSelected = {},
                )
            }
            SegmentedIconToggle(
                options = OPTIONS,
                selectedIndex = 0,
                onOptionSelected = {},
                enabled = false,
            )
        }
    }
}
