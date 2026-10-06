package app.grapheneos.camera.ui.components.focusindicator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.focusindicator.model.FocusIndicatorAppearance
import app.grapheneos.camera.ui.components.motion.FADE_IN
import app.grapheneos.camera.ui.components.motion.FADE_OUT
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.components.motion.MORPH_SPEC
import app.grapheneos.camera.ui.components.motion.SETTLE_COLOR_SPEC
import app.grapheneos.camera.ui.components.motion.SETTLE_SPEC
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.CameraPreviewViewfinder
import app.grapheneos.camera.ui.core.PREVIEW_LOCK_ICON

private val INDICATOR_SIZE = 62.dp
private val RING_DIAMETER = 60.dp
private val RING_WIDTH = 1.5.dp
private val LOCK_ICON_SIZE = 26.dp
private val LOCK_ICON_GAP = 8.dp

private const val SHOWN_SCALE_FROM = 1.3f
private const val LOCK_SCALE_FROM = 0.5f
private const val FOCUSED_ALPHA = 0.5f

/**
 * The ring is as big as the indicator, so centering the indicator on the focus point centers the
 * ring. [lockIcon] is drawn above the ring, outside the bounds, while [appearance] is
 * [FocusIndicatorAppearance.Locked].
 */
@Composable
internal fun FocusIndicator(
    visible: Boolean,
    appearance: FocusIndicatorAppearance,
    lockIcon: ImageVector,
    modifier: Modifier = Modifier,
    colors: FocusIndicatorColors = FocusIndicatorColors.fromTheme(),
) {
    val visibility = remember { MutableTransitionState(initialState = false) }
    visibility.targetState = visible

    Box(modifier = modifier.size(size = INDICATOR_SIZE)) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = scaleIn(
                animationSpec = MORPH_SPEC,
                initialScale = SHOWN_SCALE_FROM,
            ) + FADE_IN,
            exit = scaleOut(
                animationSpec = MORPH_SPEC,
                targetScale = SHOWN_SCALE_FROM,
            ) + FADE_OUT,
        ) {
            FocusIndicatorLayers(
                appearance = appearance,
                lockIcon = lockIcon,
                colors = colors,
            )
        }
    }
}

@Composable
private fun FocusIndicatorLayers(
    appearance: FocusIndicatorAppearance,
    lockIcon: ImageVector,
    colors: FocusIndicatorColors,
) {
    val rotation = LocalContentRotation.current
    val isInspecting = LocalInspectionMode.current
    val isLocked = appearance == FocusIndicatorAppearance.Locked
    val ringColor by animateColorAsState(
        targetValue = when {
            isLocked -> colors.lockedColor
            else -> colors.ringColor
        },
        animationSpec = SETTLE_COLOR_SPEC,
    )
    val ringAlpha by animateFloatAsState(
        targetValue = when (appearance) {
            FocusIndicatorAppearance.Focused -> FOCUSED_ALPHA
            else -> 1f
        },
        animationSpec = SETTLE_SPEC,
    )
    val lockVisibility = remember {
        MutableTransitionState(initialState = isInspecting && isLocked)
    }
    lockVisibility.targetState = isLocked

    Box(modifier = Modifier.size(size = INDICATOR_SIZE)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = ringColor,
                radius = RING_DIAMETER.toPx() / 2,
                alpha = ringAlpha,
                style = Stroke(width = RING_WIDTH.toPx()),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation() },
        ) {
            AnimatedVisibility(
                visibleState = lockVisibility,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -(LOCK_ICON_SIZE + LOCK_ICON_GAP)),
                enter = scaleIn(
                    animationSpec = MORPH_SPEC,
                    initialScale = LOCK_SCALE_FROM,
                ) + FADE_IN,
                exit = FADE_OUT,
            ) {
                Icon(
                    imageVector = lockIcon,
                    contentDescription = null,
                    modifier = Modifier.size(size = LOCK_ICON_SIZE),
                    tint = colors.lockedColor,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FocusIndicatorPreview() {
    CameraPreviewColumn {
        CameraPreviewViewfinder {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = LOCK_ICON_SIZE + LOCK_ICON_GAP),
                horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                FocusIndicatorAppearance.entries.forEach { appearance ->
                    FocusIndicatorLayers(
                        appearance = appearance,
                        lockIcon = PREVIEW_LOCK_ICON,
                        colors = FocusIndicatorColors.fromTheme(),
                    )
                }
            }
        }
    }
}
