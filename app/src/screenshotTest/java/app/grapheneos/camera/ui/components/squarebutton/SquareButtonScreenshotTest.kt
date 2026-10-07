package app.grapheneos.camera.ui.components.squarebutton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_RESTART_ICON
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun SquareButtonOverImage() {
    CameraPreviewColumn {
        SquareButtonStates(colors = SquareButtonColors.fromTheme())
    }
}

@PreviewTest
@PreviewLightDark
@Composable
private fun SquareButtonOnSurface() {
    CameraPreviewColumn {
        SquareButtonStates(colors = SquareButtonColors.fromSurfaceTheme())
    }
}

@Composable
private fun SquareButtonStates(
    colors: SquareButtonColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        listOf(true, false).forEach { enabled ->
            SquareButton(
                onClick = {},
                enabled = enabled,
                colors = colors,
            ) {
                Icon(
                    imageVector = PREVIEW_RESTART_ICON,
                    contentDescription = null,
                    tint = colors.contentColor,
                )
            }
        }
    }
}
