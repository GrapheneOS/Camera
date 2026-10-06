package app.grapheneos.camera.ui.components.squarebutton

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterCore
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterTone
import app.grapheneos.camera.ui.components.squarebutton.model.SquareButtonMark
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_PAUSE_ICON

private val PREVIEW_PAUSE = SquareButtonMark.Icon(
    icon = PREVIEW_PAUSE_ICON,
)
private val PREVIEW_RESUME = SquareButtonMark.Core(
    core = ShutterCore.Dot,
    tone = ShutterTone.Recording,
)

@Composable
internal fun SquareMarkButton(
    onClick: () -> Unit,
    mark: SquareButtonMark,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SquareButtonColors = SquareButtonColors.fromTheme(),
    interactionSource: MutableInteractionSource? = null,
) {
    SquareButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    ) {
        SquareButtonMarkContent(
            mark = mark,
            iconTint = colors.contentColor,
        )
    }
}

@PreviewLightDark
@Composable
private fun SquareMarkButtonPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewSquareMarkButtonRow(colors = SquareButtonColors.fromTheme())
            PreviewSquareMarkButtonRow(colors = SquareButtonColors.fromSurfaceTheme())
        }
    }
}

@Composable
private fun PreviewSquareMarkButtonRow(
    colors: SquareButtonColors,
) {
    var isPaused by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        SquareMarkButton(
            onClick = { isPaused = !isPaused },
            mark = when {
                isPaused -> PREVIEW_RESUME
                else -> PREVIEW_PAUSE
            },
            colors = colors,
        )
        SquareMarkButton(
            onClick = {},
            mark = PREVIEW_RESUME,
            colors = colors,
        )
        SquareMarkButton(
            onClick = {},
            mark = PREVIEW_PAUSE,
            enabled = false,
            colors = colors,
        )
    }
}
