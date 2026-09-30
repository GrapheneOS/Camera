package app.grapheneos.camera.ui.components.capturebutton.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

@Immutable
internal data class CaptureButtonTarget(
    val direction: CaptureButtonDirection,
    val distance: Dp,
    val icon: ImageVector,
    val accessibilityLabel: String,
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

    internal fun position(
        density: Density,
        layoutDirection: LayoutDirection,
    ): Offset {
        val distancePx = with(density) { distance.toPx() }

        return direction.unitVector(layoutDirection = layoutDirection) * distancePx
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

        return position(
            density = density,
            layoutDirection = layoutDirection,
        ) * progress
    }
}
