package app.grapheneos.camera.ui.components.squarebutton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterCore
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterTone
import app.grapheneos.camera.ui.components.squarebutton.model.SquareButtonMark
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_PAUSE_ICON
import com.android.tools.screenshot.PreviewTest

private val PAUSE = SquareButtonMark.Icon(
    icon = PREVIEW_PAUSE_ICON,
)
private val RESUME = SquareButtonMark.Core(
    core = ShutterCore.Dot,
    tone = ShutterTone.Recording,
)

@PreviewTest
@PreviewLightDark
@Composable
private fun SquareMarkButtonOverImage() {
    CameraPreviewColumn {
        SquareMarkButtonMarks(colors = SquareButtonColors.fromTheme())
    }
}

@PreviewTest
@PreviewLightDark
@Composable
private fun SquareMarkButtonOnSurface() {
    CameraPreviewColumn {
        SquareMarkButtonMarks(colors = SquareButtonColors.fromSurfaceTheme())
    }
}

@Composable
private fun SquareMarkButtonMarks(
    colors: SquareButtonColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        listOf(PAUSE, RESUME).forEach { mark ->
            listOf(true, false).forEach { enabled ->
                SquareMarkButton(
                    onClick = {},
                    mark = mark,
                    enabled = enabled,
                    colors = colors,
                )
            }
        }
    }
}
