package app.grapheneos.camera.ui.components.overlayiconbutton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_RESTART_ICON
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun OverlayIconButtonStates() {
    CameraPreviewColumn {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            listOf(true, false).forEach { enabled ->
                OverlayIconButton(
                    onClick = {},
                    icon = PREVIEW_RESTART_ICON,
                    enabled = enabled,
                )
            }
        }
    }
}
