package app.grapheneos.camera.ui.components.overlayiconbutton

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.components.motion.crossfade
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_MIC_ICON
import app.grapheneos.camera.ui.core.PREVIEW_MIC_OFF_ICON

@Composable
internal fun OverlayIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    checkedIcon: ImageVector = icon,
    enabled: Boolean = true,
    colors: OverlayIconToggleButtonColors = OverlayIconToggleButtonColors.fromTheme(),
    interactionSource: MutableInteractionSource? = null,
) {
    val rotation = LocalContentRotation.current

    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.size(size = OVERLAY_ICON_BUTTON_SIZE),
        enabled = enabled,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = colors.buttonColors.containerColor,
            contentColor = colors.buttonColors.contentColor,
            checkedContainerColor = colors.checkedContainerColor,
            checkedContentColor = colors.checkedContentColor,
        ),
        interactionSource = interactionSource,
    ) {
        AnimatedContent(
            targetState = when {
                checked -> checkedIcon
                else -> icon
            },
            modifier = Modifier.graphicsLayer { rotationZ = rotation() },
            transitionSpec = { crossfade() },
            contentAlignment = Alignment.Center,
        ) { shownIcon ->
            Icon(
                imageVector = shownIcon,
                contentDescription = null,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun OverlayIconToggleButtonPreview() {
    CameraPreviewColumn {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            var isMuted by remember { mutableStateOf(false) }

            OverlayIconToggleButton(
                checked = isMuted,
                onCheckedChange = { isMuted = it },
                icon = PREVIEW_MIC_ICON,
                checkedIcon = PREVIEW_MIC_OFF_ICON,
            )
            OverlayIconToggleButton(
                checked = true,
                onCheckedChange = {},
                icon = PREVIEW_MIC_ICON,
                checkedIcon = PREVIEW_MIC_OFF_ICON,
            )
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
