package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.compose.ui.util.lerp
import app.grapheneos.camera.ui.components.capturebutton.gesture.CaptureButtonHoldState
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import kotlinx.coroutines.flow.collectLatest

private val TARGET_SIZE = 56.dp
private val TARGET_ICON_SIZE = 24.dp
private val DOCKED_CORE_GAP = 4.dp

private const val PULL_GROWTH = 0.2f

private val PULL_SPEC = spring(
    dampingRatio = 0.7f,
    stiffness = 500f,
    visibilityThreshold = Offset.VisibilityThreshold,
)

@Composable
internal fun CaptureButtonTargetBackgrounds(
    targets: List<CaptureButtonTarget>,
    holdState: CaptureButtonHoldState,
    visibility: () -> Float,
    color: Color,
) {
    targets.fastForEach { target ->
        key(target) {
            CaptureButtonTargetBackground(
                target = target,
                holdState = holdState,
                visibility = visibility,
                color = color,
            )
        }
    }
}

@Composable
internal fun CaptureButtonTargetIcons(
    targets: List<CaptureButtonTarget>,
    holdState: CaptureButtonHoldState,
    visibility: () -> Float,
    rotation: () -> Float,
    tint: Color,
) {
    targets.fastForEach { target ->
        key(target) {
            CaptureButtonTargetIcon(
                target = target,
                holdState = holdState,
                visibility = visibility,
                rotation = rotation,
                tint = tint,
            )
        }
    }
}

@Composable
private fun CaptureButtonTargetBackground(
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
private fun CaptureButtonTargetIcon(
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

@Composable
internal fun animateHoldPull(
    holdState: CaptureButtonHoldState,
    targets: List<CaptureButtonTarget>,
): () -> Offset {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentTargets by rememberUpdatedState(targets)
    val springBack = remember {
        Animatable(
            initialValue = Offset.Zero,
            typeConverter = Offset.VectorConverter,
        )
    }

    LaunchedEffect(
        holdState,
        density,
        layoutDirection,
    ) {
        snapshotFlow {
            holdPull(
                offset = holdState.offset,
                targets = currentTargets,
                density = density,
                layoutDirection = layoutDirection,
            )
        }.collectLatest { pull ->
            when {
                holdState.isHeld -> springBack.snapTo(
                    targetValue = pull,
                )

                else -> springBack.animateTo(
                    targetValue = pull,
                    animationSpec = PULL_SPEC,
                )
            }
        }
    }

    return {
        when {
            holdState.isHeld -> holdPull(
                offset = holdState.offset,
                targets = currentTargets,
                density = density,
                layoutDirection = layoutDirection,
            )

            else -> springBack.value
        }
    }
}

@Composable
internal fun holdDockProgress(
    pull: () -> Offset,
    targets: List<CaptureButtonTarget>,
): () -> Float {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentTargets by rememberUpdatedState(targets)

    return {
        val progress = currentTargets.fastMaxOfOrNull { target ->
            target.progress(
                offset = pull(),
                density = density,
                layoutDirection = layoutDirection,
            )
        }

        progress ?: 0f
    }
}

internal fun dockedCoreSize(
    progress: Float,
    density: Density,
): Float {
    return with(density) {
        TARGET_SIZE.toPx() * targetScale(progress = progress) - DOCKED_CORE_GAP.toPx() * 2
    }
}

internal fun dockCoreSize(
    size: Float,
    dockedSize: Float,
    progress: Float,
): Float {
    return lerp(
        start = size,
        stop = size.coerceAtMost(dockedSize),
        fraction = progress,
    )
}

private fun holdPull(
    offset: Offset,
    targets: List<CaptureButtonTarget>,
    density: Density,
    layoutDirection: LayoutDirection,
): Offset {
    var strongestPull = Offset.Zero

    targets.fastForEach { target ->
        val pull = target.pull(
            offset = offset,
            density = density,
            layoutDirection = layoutDirection,
        )

        if (pull.getDistanceSquared() > strongestPull.getDistanceSquared()) {
            strongestPull = pull
        }
    }

    return strongestPull
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
