package app.grapheneos.camera.ui.core.shape

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A rectangle whose top corners curve outwards, rounding off what sits above it. The curves take
 * up the top [radius] of the shape, so content belongs below them.
 */
internal data class ConcaveTopCornersShape(
    private val cornerSize: CornerSize,
) : Shape {

    fun radius(width: Float, density: Density): Float {
        return cornerSize.toPx(
            shapeSize = Size(width = width, height = width),
            density = density,
        )
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = radius(width = size.width, density = density)
        val path = Path().apply {
            moveTo(x = 0f, y = radius)
            lineTo(x = 0f, y = 0f)
            arcTo(
                rect = Rect(center = Offset(x = radius, y = 0f), radius = radius),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false,
            )
            lineTo(x = size.width - radius, y = radius)
            arcTo(
                rect = Rect(center = Offset(x = size.width - radius, y = 0f), radius = radius),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false,
            )
            lineTo(x = size.width, y = size.height)
            lineTo(x = 0f, y = size.height)
            close()
        }

        return Outline.Generic(path = path)
    }
}
