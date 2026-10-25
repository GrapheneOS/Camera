package app.grapheneos.camera.ui.gallery.components.pager

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.animation.follow
import app.grapheneos.camera.ui.gallery.components.pager.gesture.ViewerTransformState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val SETTLE_MILLIS = 200
private const val EXIT_MILLIS = 200

private val EXIT_DISTANCE = 240.dp

@Composable
internal fun rememberViewerTransform(
    transformState: ViewerTransformState,
    onDismissed: () -> Unit,
): ViewerTransform {
    val exitDistance = with(LocalDensity.current) { EXIT_DISTANCE.toPx() }
    val viewerTransform = remember(transformState, exitDistance) {
        ViewerTransform(
            transformState = transformState,
            exitDistance = exitDistance,
        )
    }
    val currentOnDismissed by rememberUpdatedState(onDismissed)

    LaunchedEffect(viewerTransform) {
        viewerTransform.followGesture()
    }

    LaunchedEffect(transformState.isDismissed) {
        if (transformState.isDismissed) {
            viewerTransform.exit()
            currentOnDismissed()
        }
    }

    return viewerTransform
}

@Stable
internal class ViewerTransform(
    private val transformState: ViewerTransformState,
    private val exitDistance: Float,
) {

    private val offset = Animatable(transformState.offset, Offset.VectorConverter)
    private val scale = Animatable(transformState.scale)
    private val rotation = Animatable(transformState.rotation)
    private val fade = Animatable(0f)
    private val exitProgress = Animatable(0f)

    suspend fun followGesture() {
        coroutineScope {
            launchFollow(offset) { transformState.offset }
            launchFollow(scale) { transformState.scale }
            launchFollow(rotation) { transformState.rotation }
            launchFollow(fade) { transformState.fadeProgress() }
        }
    }

    suspend fun exit() {
        exitProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = EXIT_MILLIS),
        )
    }

    fun fadeProgress(): Float {
        return fade.value
    }

    fun isAtRest(): Boolean {
        return transformState.isAtFit() &&
            scale.value == transformState.scale &&
            offset.value == transformState.offset &&
            rotation.value == transformState.rotation
    }

    fun isRotated(): Boolean {
        return rotation.value != 0f
    }

    fun applyTo(layer: GraphicsLayerScope) {
        val exitShift = transformState.exitDirection * exitDistance * exitProgress.value

        layer.translationX = offset.value.x
        layer.translationY = offset.value.y + exitShift
        layer.scaleX = scale.value
        layer.scaleY = scale.value
        layer.rotationZ = rotation.value
        layer.alpha = 1f - exitProgress.value
    }

    private fun <T, V : AnimationVector> CoroutineScope.launchFollow(
        animatable: Animatable<T, V>,
        target: () -> T,
    ) {
        launch {
            animatable.follow(
                target = target,
                isAnimated = { !transformState.isTransforming },
                animationSpec = tween(durationMillis = SETTLE_MILLIS),
            )
        }
    }
}
