package app.grapheneos.camera.ui.gallery.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
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
import app.grapheneos.camera.ui.core.CameraPreviewTheme
import app.grapheneos.camera.ui.core.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.previewShot
import app.grapheneos.camera.ui.gallery.components.gesture.ZoomGestureState
import app.grapheneos.camera.ui.gallery.components.gesture.zoomTransformInput
import kotlin.math.min
import kotlinx.coroutines.flow.drop

private const val DOUBLE_TAP_ANIMATION_MILLIS = 200

@Composable
internal fun ZoomableImage(
    image: ImageBitmap,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val gestureState = remember { ZoomGestureState() }
    val onZoomedOut = remember(hapticFeedback) {
        { hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)
    val scale by animateFloatAsState(
        targetValue = gestureState.scale,
        animationSpec = zoomAnimationSpec(isEnabled = gestureState.isZoomAnimationEnabled),
    )
    val offset by animateOffsetAsState(
        targetValue = gestureState.offset,
        animationSpec = zoomAnimationSpec(isEnabled = gestureState.isZoomAnimationEnabled),
    )

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
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
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
            .drop(1)
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

private fun <T> zoomAnimationSpec(isEnabled: Boolean): AnimationSpec<T> {
    return when {
        isEnabled -> tween(
            durationMillis = DOUBLE_TAP_ANIMATION_MILLIS,
            easing = FastOutSlowInEasing,
        )
        else -> snap()
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
