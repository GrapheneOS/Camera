package app.grapheneos.camera.ui.gallery.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.animation.follow
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.core.preview.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.preview.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.preview.previewShot
import app.grapheneos.camera.ui.gallery.components.gesture.ZoomGestureState
import app.grapheneos.camera.ui.gallery.components.gesture.zoomTransformInput
import kotlin.math.min
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val DOUBLE_TAP_ANIMATION_MILLIS = 200

@Composable
internal fun ZoomableImage(
    image: ImageBitmap,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val gestureState = rememberSaveable(saver = ZoomGestureState.Saver) { ZoomGestureState() }
    val onZoomedOut = remember(hapticFeedback) {
        { hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)
    val zoomTransform = rememberZoomTransform(gestureState)

    ZoomEffects(
        gestureState = gestureState,
        image = image,
        containerSize = containerSize,
        onZoomChanged = onZoomChanged,
    )

    Image(
        bitmap = image,
        contentDescription = stringResource(R.string.gallery_photo),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(gestureState) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { tapOffset -> gestureState.applyDoubleTap(tapOffset) },
                )
            }
            .zoomTransformInput(
                gestureState = gestureState,
                onZoomedOut = onZoomedOut,
            )
            .graphicsLayer(zoomTransform::applyTo),
    )
}

@Composable
private fun rememberZoomTransform(
    gestureState: ZoomGestureState,
): ZoomTransform {
    val zoomTransform = remember(gestureState) { ZoomTransform(gestureState) }

    LaunchedEffect(zoomTransform) {
        zoomTransform.followGesture()
    }

    return zoomTransform
}

@Composable
private fun ZoomEffects(
    gestureState: ZoomGestureState,
    image: ImageBitmap,
    containerSize: IntSize,
    onZoomChanged: (Boolean) -> Unit,
) {
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    LaunchedEffect(
        gestureState,
        containerSize,
        image.width,
        image.height,
    ) {
        if (containerSize == IntSize.Zero) return@LaunchedEffect

        gestureState.updateLayout(
            containerSize = containerSize.toSize(),
            contentSize = fittedSize(
                image = image,
                containerSize = containerSize,
            ),
        )
    }

    LaunchedEffect(gestureState) {
        snapshotFlow { gestureState.isZoomed() }
            .collect { isZoomed -> currentOnZoomChanged(isZoomed) }
    }
}

private fun fittedSize(
    image: ImageBitmap,
    containerSize: IntSize,
): Size {
    val fitScale = min(
        containerSize.width.toFloat() / image.width,
        containerSize.height.toFloat() / image.height,
    )

    return Size(
        width = image.width * fitScale,
        height = image.height * fitScale,
    )
}

private class ZoomTransform(
    private val gestureState: ZoomGestureState,
) {

    private val scale = Animatable(gestureState.scale)
    private val offset = Animatable(gestureState.offset, Offset.VectorConverter)

    fun applyTo(layer: GraphicsLayerScope) {
        layer.scaleX = scale.value
        layer.scaleY = scale.value
        layer.translationX = offset.value.x
        layer.translationY = offset.value.y
    }

    suspend fun followGesture() {
        val isAnimated = { gestureState.isZoomAnimationEnabled }

        coroutineScope {
            launch {
                scale.follow(
                    target = { gestureState.scale },
                    isAnimated = isAnimated,
                    animationSpec = doubleTapSpec(),
                )
            }
            launch {
                offset.follow(
                    target = { gestureState.offset },
                    isAnimated = isAnimated,
                    animationSpec = doubleTapSpec(),
                )
            }
        }
    }

    private fun <T> doubleTapSpec(): AnimationSpec<T> {
        return tween(
            durationMillis = DOUBLE_TAP_ANIMATION_MILLIS,
            easing = FastOutSlowInEasing,
        )
    }
}

@PreviewLightDark
@Composable
private fun ZoomableImagePreview() {
    CameraPreviewTheme {
        ZoomableImage(
            image = previewShot(
                top = PREVIEW_COOL_ICON,
                bottom = PREVIEW_SCENE,
            ),
            onTap = {},
            onZoomChanged = {},
        )
    }
}
