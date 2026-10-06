package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.SETTLE_SPEC

private val ICON_SIZE = 24.dp

@Composable
internal fun CaptureButtonIcon(
    icon: ImageVector?,
    isPressed: Boolean,
    isHeld: Boolean,
    tint: Color,
    pull: () -> Offset,
    rotation: () -> Float,
    modifier: Modifier = Modifier,
) {
    val pressedScale by animatePressedScale(
        isPressed = isPressed,
        isHeld = isHeld,
    )

    Crossfade(
        targetState = CaptureButtonIconLook(icon = icon, tint = tint),
        modifier = modifier.graphicsLayer {
            scaleX = pressedScale
            scaleY = pressedScale
            translationX = pull().x
            translationY = pull().y
            rotationZ = rotation()
        },
        animationSpec = SETTLE_SPEC,
    ) { look ->
        if (look.icon != null) {
            Icon(
                imageVector = look.icon,
                contentDescription = null,
                modifier = Modifier.size(size = ICON_SIZE),
                tint = look.tint,
            )
        }
    }
}

@Immutable
private data class CaptureButtonIconLook(
    val icon: ImageVector?,
    val tint: Color,
)
