package app.grapheneos.camera.ui.components.overlayiconbutton

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_RESTART_ICON

internal val OVERLAY_ICON_BUTTON_SIZE = 48.dp

@Composable
internal fun OverlayIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: OverlayIconButtonColors = OverlayIconButtonColors.fromTheme(),
    interactionSource: MutableInteractionSource? = null,
) {
    val rotation = LocalContentRotation.current

    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(size = OVERLAY_ICON_BUTTON_SIZE),
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = colors.containerColor,
            contentColor = colors.contentColor,
        ),
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = rotation() },
        )
    }
}

@PreviewLightDark
@Composable
private fun OverlayIconButtonPreview() {
    CameraPreviewColumn {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            OverlayIconButton(
                onClick = {},
                icon = PREVIEW_RESTART_ICON,
            )
            OverlayIconButton(
                onClick = {},
                icon = PREVIEW_RESTART_ICON,
                enabled = false,
            )
        }
    }
}
