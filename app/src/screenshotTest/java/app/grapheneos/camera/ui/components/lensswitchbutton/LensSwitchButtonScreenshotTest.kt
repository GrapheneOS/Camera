package app.grapheneos.camera.ui.components.lensswitchbutton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.squarebutton.SquareButtonColors
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_LENS_SWITCH_ICON
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun LensSwitchButtonOverImage() {
    CameraPreviewColumn {
        LensSwitchButtonRow(colors = SquareButtonColors.fromTheme())
    }
}

@PreviewTest
@PreviewLightDark
@Composable
private fun LensSwitchButtonOnSurface() {
    CameraPreviewColumn {
        LensSwitchButtonRow(colors = SquareButtonColors.fromSurfaceTheme())
    }
}

@Composable
private fun LensSwitchButtonRow(
    colors: SquareButtonColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        listOf(true, false).forEach { enabled ->
            LensSwitchButton(
                flipped = false,
                onClick = {},
                icon = PREVIEW_LENS_SWITCH_ICON,
                enabled = enabled,
                colors = colors,
            )
        }
    }
}
