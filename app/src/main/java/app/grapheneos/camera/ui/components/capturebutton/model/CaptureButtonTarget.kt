package app.grapheneos.camera.ui.components.capturebutton.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Immutable
internal data class CaptureButtonTarget(
    val direction: CaptureButtonDirection,
    val accessibilityLabel: String,
    val distance: Dp,
) {

    internal fun progress(
        offset: Offset,
        density: Density,
        layoutDirection: LayoutDirection,
    ): Float {
        val unitVector = direction.unitVector(layoutDirection = layoutDirection)
        val travelled = offset.x * unitVector.x + offset.y * unitVector.y
        val distancePx = with(density) { distance.toPx() }

        return (travelled / distancePx).coerceIn(0f, 1f)
    }

    internal fun pull(
        offset: Offset,
        density: Density,
        layoutDirection: LayoutDirection,
    ): Offset {
        val progress = progress(
            offset = offset,
            density = density,
            layoutDirection = layoutDirection,
        )
        val distancePx = with(density) { distance.toPx() }

        return direction.unitVector(layoutDirection = layoutDirection) * (progress * distancePx)
    }
}
