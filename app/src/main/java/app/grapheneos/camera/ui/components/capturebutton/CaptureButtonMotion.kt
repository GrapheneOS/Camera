package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.components.motion.LIFT_SPEC
import app.grapheneos.camera.ui.components.motion.MORPH_SPEC
import app.grapheneos.camera.ui.components.motion.SETTLE_COLOR_SPEC

private const val PRESSED_SCALE = 1.1f
private const val HELD_SCALE = 1.15f
private const val PRESSED_ALPHA = 0.8f

@Composable
internal fun animatePressedScale(
    isPressed: Boolean,
    isHeld: Boolean,
): State<Float> {
    return animateFloatAsState(
        targetValue = when {
            isHeld -> HELD_SCALE
            isPressed -> PRESSED_SCALE
            else -> 1f
        },
        animationSpec = when {
            isHeld -> LIFT_SPEC
            else -> MORPH_SPEC
        },
    )
}

@Composable
internal fun animatePressedColor(
    color: Color,
    isPressed: Boolean,
    isHeld: Boolean,
): State<Color> {
    return animateColorAsState(
        targetValue = when {
            isPressed && !isHeld -> color.copy(alpha = color.alpha * PRESSED_ALPHA)
            else -> color
        },
        animationSpec = when {
            isHeld -> SETTLE_COLOR_SPEC
            else -> spring()
        },
    )
}

@Composable
internal fun animateVisibility(isVisible: Boolean): State<Float> {
    return animateFloatAsState(
        targetValue = when {
            isVisible -> 1f
            else -> 0f
        },
    )
}
