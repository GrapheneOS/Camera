package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private const val PRESSED_SCALE = 1.1f
private const val HELD_SCALE = 1.15f
private const val PRESSED_ALPHA = 0.8f
private const val DISABLED_ALPHA = 0.35f
private const val FULL_TURN = 360f
private const val HALF_TURN = 180f

internal val MORPH_SPEC = spring<Float>(
    dampingRatio = 0.7f,
    stiffness = 500f,
)
private val LIFT_SPEC = spring<Float>(
    dampingRatio = 0.5f,
    stiffness = 500f,
)
private val LIFT_COLOR_SPEC = spring<Color>(
    stiffness = 500f,
)

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
            isHeld -> LIFT_COLOR_SPEC
            else -> spring()
        },
    )
}

@Composable
internal fun animateEnabledAlpha(enabled: Boolean): State<Float> {
    return animateFloatAsState(
        targetValue = when {
            enabled -> 1f
            else -> DISABLED_ALPHA
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

@Composable
internal fun animateRotation(degrees: Float): State<Float> {
    val rotation = remember { Animatable(initialValue = degrees) }

    LaunchedEffect(degrees) {
        rotation.animateTo(
            targetValue = rotation.value + shortestTurn(
                from = rotation.value,
                to = degrees,
            ),
            animationSpec = MORPH_SPEC,
        )
    }

    return rotation.asState()
}

internal fun shortestTurn(
    from: Float,
    to: Float,
): Float {
    return ((to - from) % FULL_TURN + FULL_TURN + HALF_TURN) % FULL_TURN - HALF_TURN
}
