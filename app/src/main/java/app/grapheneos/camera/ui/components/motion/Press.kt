package app.grapheneos.camera.ui.components.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

@Composable
internal fun animatePressScale(
    isPressed: Boolean,
    pressedScale: Float,
): State<Float> {
    return animateFloatAsState(
        targetValue = when {
            isPressed -> pressedScale
            else -> 1f
        },
        animationSpec = QUICK_SPEC,
    )
}
