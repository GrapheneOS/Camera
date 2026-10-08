package app.grapheneos.camera.ui.components.overlayiconbutton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_MIC_ICON
import app.grapheneos.camera.ui.core.PREVIEW_MIC_OFF_ICON
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun OverlayIconToggleButtonStates() {
    CameraPreviewColumn {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            listOf(false, true).forEach { checked ->
                OverlayIconToggleButton(
                    checked = checked,
                    onCheckedChange = {},
                    icon = PREVIEW_MIC_ICON,
                    checkedIcon = PREVIEW_MIC_OFF_ICON,
                )
            }
            OverlayIconToggleButton(
                checked = false,
                onCheckedChange = {},
                icon = PREVIEW_MIC_ICON,
                checkedIcon = PREVIEW_MIC_OFF_ICON,
                enabled = false,
            )
        }
    }
}
