package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import app.grapheneos.camera.ui.components.capturebutton.gesture.CaptureButtonHoldState
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget

private val TARGET_SIZE = 56.dp
private val TARGET_ICON_SIZE = 24.dp
private val DOCKED_CORE_GAP = 4.dp

private const val PULL_GROWTH = 0.2f

@Composable
internal fun CaptureButtonTargetBackground(
    target: CaptureButtonTarget,
    holdState: CaptureButtonHoldState,
    visibility: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .targetPlacement(
                target = target,
                holdState = holdState,
                visibility = visibility,
                layoutDirection = LocalLayoutDirection.current,
            )
            .size(TARGET_SIZE)
            .drawBehind {
                drawCircle(color = color)
            },
    )
}

@Composable
internal fun CaptureButtonTargetIcon(
    target: CaptureButtonTarget,
    holdState: CaptureButtonHoldState,
    visibility: () -> Float,
    rotation: () -> Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = target.icon,
        contentDescription = null,
        modifier = modifier
            .targetPlacement(
                target = target,
                holdState = holdState,
                visibility = visibility,
                layoutDirection = LocalLayoutDirection.current,
            )
            .graphicsLayer {
                rotationZ = rotation()
            }
            .size(TARGET_ICON_SIZE),
        tint = tint,
    )
}

internal fun dockedCoreSize(
    progress: Float,
    density: Density,
): Float {
    return with(density) {
        TARGET_SIZE.toPx() * targetScale(progress = progress) - DOCKED_CORE_GAP.toPx() * 2
    }
}

private fun targetScale(progress: Float): Float {
    return 1f + PULL_GROWTH * progress
}

private fun Modifier.targetPlacement(
    target: CaptureButtonTarget,
    holdState: CaptureButtonHoldState,
    visibility: () -> Float,
    layoutDirection: LayoutDirection,
): Modifier {
    return this
        .offset {
            target
                .position(
                    density = this,
                    layoutDirection = layoutDirection,
                )
                .round()
        }
        .graphicsLayer {
            val progress = target.progress(
                offset = holdState.offset,
                density = this,
                layoutDirection = layoutDirection,
            )
            val scale = visibility() * targetScale(progress = progress)

            alpha = visibility()
            scaleX = scale
            scaleY = scale
        }
}
