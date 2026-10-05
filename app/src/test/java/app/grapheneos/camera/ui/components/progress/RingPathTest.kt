package app.grapheneos.camera.ui.components.progress

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RingPathTest {

    private val circle = RingPath(
        size = Size(width = SIZE, height = SIZE),
        strokeWidth = STROKE,
        cornerRadius = SIZE / 2,
    )

    @Test
    fun fullCorners_makeACircleThroughTheStrokesMiddle() {
        val radius = (SIZE - STROKE) / 2

        assertLengthEquals((2 * PI * radius).toFloat(), circle.length)
    }

    @Test
    fun smallerCorners_addTheStraightSides() {
        val square = RingPath(
            size = Size(width = SIZE, height = SIZE),
            strokeWidth = STROKE,
            cornerRadius = CORNER,
        )
        val radius = CORNER - STROKE / 2
        val straight = SIZE - STROKE - 2 * radius

        assertLengthEquals((4 * straight + 2 * PI * radius).toFloat(), square.length)
    }

    @Test
    fun ring_startsAtTheTopCenter() {
        assertOffsetEquals(Offset(x = SIZE / 2, y = STROKE / 2), circle.position(distance = 0f))
    }

    @Test
    fun ring_runsClockwise() {
        val quarter = circle.position(distance = circle.length / 4)

        assertOffsetEquals(Offset(x = SIZE - STROKE / 2, y = SIZE / 2), quarter)
    }

    @Test
    fun aNegativeDistance_countsBackFromTheStart() {
        val behind = circle.position(distance = -circle.length / 4)

        assertOffsetEquals(circle.position(distance = circle.length * 3 / 4), behind)
    }

    private fun assertOffsetEquals(
        expected: Offset,
        actual: Offset,
    ) {
        assertEquals(expected.x, actual.x, POSITION_TOLERANCE)
        assertEquals(expected.y, actual.y, POSITION_TOLERANCE)
    }

    private fun assertLengthEquals(
        expected: Float,
        actual: Float,
    ) {
        assertEquals(expected, actual, expected * LENGTH_TOLERANCE)
    }

    private companion object {
        private const val SIZE = 100f
        private const val STROKE = 10f
        private const val CORNER = 20f
        private const val POSITION_TOLERANCE = 0.5f
        private const val LENGTH_TOLERANCE = 0.005f
    }
}
