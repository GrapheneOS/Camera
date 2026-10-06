package app.grapheneos.camera.ui.components.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private const val FULL_TURN = 360f
private const val HALF_TURN = 180f

internal val LocalContentRotation = staticCompositionLocalOf { { 0f } }

@Composable
internal fun ProvideContentRotation(
    degrees: Float,
    content: @Composable () -> Unit,
) {
    val rotation = animateRotation(degrees = degrees)
    val readRotation = remember(rotation) { { rotation.value } }

    CompositionLocalProvider(
        LocalContentRotation provides readRotation,
        content = content,
    )
}

@Composable
private fun animateRotation(degrees: Float): State<Float> {
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
