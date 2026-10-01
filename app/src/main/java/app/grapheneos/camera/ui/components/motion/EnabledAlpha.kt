package app.grapheneos.camera.ui.components.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

private const val DISABLED_ALPHA = 0.35f

@Composable
internal fun animateEnabledAlpha(enabled: Boolean): State<Float> {
    return animateFloatAsState(
        targetValue = when {
            enabled -> 1f
            else -> DISABLED_ALPHA
        },
    )
}
