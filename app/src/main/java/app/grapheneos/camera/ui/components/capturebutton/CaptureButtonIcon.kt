package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.QUICK_COLOR_SPEC
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
    val animatedTint by animateColorAsState(
        targetValue = tint,
        animationSpec = QUICK_COLOR_SPEC,
    )
    val pressedScale by animatePressedScale(
        isPressed = isPressed,
        isHeld = isHeld,
    )

    Crossfade(
        targetState = icon,
        modifier = modifier.graphicsLayer {
            scaleX = pressedScale
            scaleY = pressedScale
            translationX = pull().x
            translationY = pull().y
            rotationZ = rotation()
        },
        animationSpec = SETTLE_SPEC,
    ) { targetIcon ->
        if (targetIcon != null) {
            Icon(
                imageVector = targetIcon,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
                tint = animatedTint,
            )
        }
    }
}
