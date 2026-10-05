package app.grapheneos.camera.ui.components.thumbnailbutton

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class InsetCornerShapeTest {

    @Test
    fun insetCorners_areConcentricWithTheOuterShape() {
        val corner = innerCorner(outer = RoundedCornerShape(size = 12.dp))

        assertEquals(CornerRadius(x = 10f), corner)
    }

    @Test
    fun percentCorners_areResolvedAgainstTheOuterSize() {
        val corner = innerCorner(outer = CircleShape)

        assertEquals(CornerRadius(x = OUTER_SIZE / 2 - INSET), corner)
    }

    @Test
    fun anInsetDeeperThanTheCorner_leavesASquareCorner() {
        val corner = innerCorner(outer = RoundedCornerShape(size = 1.dp))

        assertEquals(CornerRadius.Zero, corner)
    }

    private fun innerCorner(outer: CornerBasedShape): CornerRadius {
        val innerSize = OUTER_SIZE - 2 * INSET
        val outline = InsetCornerShape(outer = outer, inset = INSET.dp).createOutline(
            size = Size(width = innerSize, height = innerSize),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(density = 1f),
        )

        return (outline as Outline.Rounded).roundRect.topLeftCornerRadius
    }

    private companion object {
        private const val OUTER_SIZE = 60f
        private const val INSET = 2f
    }
}
