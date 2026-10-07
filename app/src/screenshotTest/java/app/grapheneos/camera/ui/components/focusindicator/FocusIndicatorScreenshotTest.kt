package app.grapheneos.camera.ui.components.focusindicator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.focusindicator.model.FocusIndicatorAppearance
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.CameraPreviewViewfinder
import app.grapheneos.camera.ui.core.PREVIEW_LOCK_ICON
import com.android.tools.screenshot.PreviewTest

private val LOCK_ICON_ROOM = 34.dp

@PreviewTest
@PreviewLightDark
@Composable
private fun FocusIndicatorAppearances() {
    CameraPreviewColumn {
        CameraPreviewViewfinder {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = LOCK_ICON_ROOM),
                horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                FocusIndicatorAppearance.entries.forEach { appearance ->
                    FocusIndicator(
                        visible = true,
                        appearance = appearance,
                        lockIcon = PREVIEW_LOCK_ICON,
                    )
                }
            }
        }
    }
}
