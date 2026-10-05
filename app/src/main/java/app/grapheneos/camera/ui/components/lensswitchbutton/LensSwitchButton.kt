package app.grapheneos.camera.ui.components.lensswitchbutton

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.squarebutton.SquareButton
import app.grapheneos.camera.ui.components.squarebutton.SquareButtonColors
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_LENS_SWITCH_ICON

private val ICON_SIZE = 32.dp

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
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(size = ICON_SIZE)
                .graphicsLayer { rotationZ = turn.value },
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
