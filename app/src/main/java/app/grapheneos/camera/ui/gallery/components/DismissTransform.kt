package app.grapheneos.camera.ui.gallery.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.follow
import app.grapheneos.camera.ui.gallery.components.gesture.DismissGestureState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val RETURN_MILLIS = 200
private const val EXIT_MILLIS = 200

private val EXIT_DISTANCE = 240.dp

@Composable
internal fun rememberDismissTransform(
    dismissGestureState: DismissGestureState,
    onDismissed: () -> Unit,
): DismissTransform {
    val exitDistance = with(LocalDensity.current) { EXIT_DISTANCE.toPx() }
    val dismissTransform = remember(dismissGestureState, exitDistance) {
        DismissTransform(
            dismissGestureState = dismissGestureState,
            exitDistance = exitDistance,
        )
    }
    val currentOnDismissed by rememberUpdatedState(onDismissed)

    LaunchedEffect(dismissTransform) {
        dismissTransform.followGesture()
    }

    LaunchedEffect(dismissGestureState.isDismissed) {
        if (dismissGestureState.isDismissed) {
            dismissTransform.exit()
            currentOnDismissed()
        }
    }

    return dismissTransform
}

internal class DismissTransform(
    private val dismissGestureState: DismissGestureState,
    private val exitDistance: Float,
) {

    private val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    private val scale = Animatable(1f)
    private val rotation = Animatable(0f)
    private val exitProgress = Animatable(0f)

    suspend fun followGesture() {
        val isReturning = { !dismissGestureState.isDragging }

        coroutineScope {
            launch {
                offset.follow(
                    target = { dismissGestureState.offset },
                    isAnimated = isReturning,
                    animationSpec = tween(durationMillis = RETURN_MILLIS),
                )
            }
            launch {
                scale.follow(
                    target = { dismissGestureState.scale },
                    isAnimated = isReturning,
                    animationSpec = tween(durationMillis = RETURN_MILLIS),
                )
            }
            launch {
                rotation.follow(
                    target = { dismissGestureState.rotation },
                    isAnimated = isReturning,
                    animationSpec = tween(durationMillis = RETURN_MILLIS),
                )
            }
        }
    }

    suspend fun exit() {
        exitProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = EXIT_MILLIS),
        )
    }

    fun progress(): Float {
        return dismissGestureState.progressAt(scale.value)
    }

    fun applyTo(layer: GraphicsLayerScope) {
        val origin = dismissGestureState.origin
        val exitShift = dismissGestureState.exitDirection * exitDistance * exitProgress.value

        if (layer.size.width > 0f && layer.size.height > 0f) {
            layer.transformOrigin = TransformOrigin(
                pivotFractionX = origin.x / layer.size.width,
                pivotFractionY = origin.y / layer.size.height,
            )
        }

        layer.translationX = offset.value.x
        layer.translationY = offset.value.y + exitShift
        layer.scaleX = scale.value
        layer.scaleY = scale.value
        layer.rotationZ = rotation.value
        layer.alpha = 1f - exitProgress.value
    }
}
