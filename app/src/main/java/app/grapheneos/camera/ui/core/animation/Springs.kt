package app.grapheneos.camera.ui.core.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

private const val SETTLE_STIFFNESS = 500f

internal val SETTLE_SPEC = settleSpring<Float>()
internal val SETTLE_SIZE_SPEC = settleSpring<IntSize>()
internal val SETTLE_COLOR_SPEC = settleSpring<Color>()

private fun <T> settleSpring(): SpringSpec<T> {
    return spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = SETTLE_STIFFNESS,
    )
}
