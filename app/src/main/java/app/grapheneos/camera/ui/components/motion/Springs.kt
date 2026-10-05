package app.grapheneos.camera.ui.components.motion

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

private const val STIFFNESS = 500f
private const val MORPH_DAMPING = Spring.DampingRatioLowBouncy
private const val LIFT_DAMPING = Spring.DampingRatioMediumBouncy
private const val SETTLE_DAMPING = Spring.DampingRatioNoBouncy

internal val MORPH_SPEC = springOf<Float>(dampingRatio = MORPH_DAMPING)
internal val MORPH_OFFSET_SPEC = springOf(
    dampingRatio = MORPH_DAMPING,
    visibilityThreshold = Offset.VisibilityThreshold,
)
internal val MORPH_INT_OFFSET_SPEC = springOf(
    dampingRatio = MORPH_DAMPING,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

internal val LIFT_SPEC = springOf<Float>(dampingRatio = LIFT_DAMPING)
internal val LIFT_FLOAT_SPEC = FloatSpringSpec(
    dampingRatio = LIFT_DAMPING,
    stiffness = STIFFNESS,
)

internal val SETTLE_SPEC = springOf<Float>(dampingRatio = SETTLE_DAMPING)
internal val SETTLE_SIZE_SPEC = springOf<IntSize>(dampingRatio = SETTLE_DAMPING)
internal val SETTLE_COLOR_SPEC = springOf<Color>(dampingRatio = SETTLE_DAMPING)

private fun <T> springOf(
    dampingRatio: Float,
    visibilityThreshold: T? = null,
): SpringSpec<T> {
    return spring(
        dampingRatio = dampingRatio,
        stiffness = STIFFNESS,
        visibilityThreshold = visibilityThreshold,
    )
}
