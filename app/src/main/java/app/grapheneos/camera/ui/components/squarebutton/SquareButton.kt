package app.grapheneos.camera.ui.components.squarebutton

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.animateEnabledAlpha
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_RESTART_ICON

private val BUTTON_SIZE = 60.dp

internal val SQUARE_BUTTON_SHAPE = RoundedCornerShape(size = 12.dp)

@Composable
internal fun SquareButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    colors: SquareButtonColors = SquareButtonColors.fromTheme(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    require(onLongClick != null || onLongClickLabel == null) {
        "onLongClickLabel needs onLongClick"
    }

    val alpha by animateEnabledAlpha(enabled = enabled)

    Box(
        modifier = modifier
            .size(size = BUTTON_SIZE)
            .graphicsLayer { this.alpha = alpha }
            .clip(shape = SQUARE_BUTTON_SHAPE)
            .background(color = colors.containerColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(color = colors.contentColor),
                enabled = enabled,
                onLongClickLabel = onLongClickLabel,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun SquareButtonPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewSquareButtonRow(colors = SquareButtonColors.fromTheme())
            PreviewSquareButtonRow(colors = SquareButtonColors.fromSurfaceTheme())
        }
    }
}

@Composable
private fun PreviewSquareButtonRow(
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
