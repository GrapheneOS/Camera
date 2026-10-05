package app.grapheneos.camera.ui.components.thumbnailbutton

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

@Immutable
internal data class InsetCornerShape(
    private val outer: CornerBasedShape,
    private val inset: Dp,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val insetPx = with(density) { inset.toPx() }
        val outerSize = Size(
            width = size.width + 2 * insetPx,
            height = size.height + 2 * insetPx,
        )

        fun insetCorner(corner: CornerSize): CornerSize {
            return CornerSize(size = (corner.toPx(outerSize, density) - insetPx).coerceAtLeast(0f))
        }

        return outer
            .copy(
                topStart = insetCorner(outer.topStart),
                topEnd = insetCorner(outer.topEnd),
                bottomEnd = insetCorner(outer.bottomEnd),
                bottomStart = insetCorner(outer.bottomStart),
            )
            .createOutline(
                size = size,
                layoutDirection = layoutDirection,
                density = density,
            )
    }
}
