package app.grapheneos.camera.ui.components.lensswitchbutton

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.squarebutton.SquareButton
import app.grapheneos.camera.ui.components.squarebutton.SquareButtonColors
import app.grapheneos.camera.ui.components.squarebutton.SquareButtonMarkContent
import app.grapheneos.camera.ui.components.squarebutton.model.SquareButtonMark
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn
import app.grapheneos.camera.ui.core.preview.PREVIEW_LENS_SWITCH_ICON

/**
 * @param flipped which camera is in use. Every change turns the icon half a turn
 * counterclockwise, so it turns when the camera is switched some other way too, and stays still
 * when a tap does not switch it.
 */
@Composable
internal fun LensSwitchButton(
    flipped: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SquareButtonColors = SquareButtonColors.fromTheme(),
    interactionSource: MutableInteractionSource? = null,
) {
    val turn = animateFlipTurn(flipped = flipped)

    SquareButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    ) {
        SquareButtonMarkContent(
            mark = SquareButtonMark.Icon(icon = icon),
            iconTint = colors.contentColor,
            turn = { turn.value },
        )
    }
}

@PreviewLightDark
@Composable
private fun LensSwitchButtonPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewLensSwitchButtonRow(colors = SquareButtonColors.fromTheme())
            PreviewLensSwitchButtonRow(colors = SquareButtonColors.fromSurfaceTheme())
        }
    }
}

@Composable
private fun PreviewLensSwitchButtonRow(
    colors: SquareButtonColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        listOf(true, false).forEach { enabled ->
            var flipped by remember { mutableStateOf(false) }

            LensSwitchButton(
                flipped = flipped,
                onClick = { flipped = !flipped },
                icon = PREVIEW_LENS_SWITCH_ICON,
                enabled = enabled,
                colors = colors,
            )
        }
    }
}
