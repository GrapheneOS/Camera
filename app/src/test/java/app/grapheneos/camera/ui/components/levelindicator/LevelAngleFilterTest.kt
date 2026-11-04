package app.grapheneos.camera.ui.components.levelindicator

import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelAngleFilterTest {

    @Test
    fun firstReading_isShownImmediately() {
        val filter = LevelAngleFilter(wrapsAtFullTurn = true)

        assertEquals(4.25f, filter.update(degrees = 4.25f, elapsed = Duration.ZERO))
    }

    @Test
    fun smallMovements_keepTheLastAngleUntilTheyAccumulate() {
        val filter = LevelAngleFilter(wrapsAtFullTurn = true)
        filter.update(degrees = 4f, elapsed = Duration.ZERO)

        assertEquals(4f, filter.update(degrees = 4.1f, elapsed = FRAME_INTERVAL))
        assertEquals(4f, filter.update(degrees = 3.8f, elapsed = FRAME_INTERVAL))

        val changed = filter.update(degrees = 4.4f, elapsed = FRAME_INTERVAL)
        assertTrue(changed > 4f)
        assertTrue(changed < 4.4f)
    }

    @Test
    fun tremblingAtRest_isReducedToLessThanAQuarterOfTheInput() {
        val filter = LevelAngleFilter(wrapsAtFullTurn = true)
        filter.update(degrees = 4f, elapsed = Duration.ZERO)

        var maximumDeviation = 0f
        repeat(120) { index ->
            val input = when (index % 2) {
                0 -> 4.6f
                else -> 3.4f
            }
            val shown = filter.update(degrees = input, elapsed = FRAME_INTERVAL)
            maximumDeviation = maxOf(maximumDeviation, abs(shown - 4f))
        }

        assertTrue(maximumDeviation < 0.15f)
    }

    @Test
    fun deliberateTurn_reachesTheNewAngleWithinOneHundredMilliseconds() {
        val filter = LevelAngleFilter(wrapsAtFullTurn = true)
        filter.update(degrees = 0f, elapsed = Duration.ZERO)

        var shown = 0f
        repeat(6) {
            shown = filter.update(degrees = 30f, elapsed = FRAME_INTERVAL)
        }

        assertTrue(shown > 29f)
        assertTrue(shown <= 30f)
    }

    @Test
    fun pitchCrossingZero_movesThroughTheHorizonInsteadOfWrapping() {
        val filter = LevelAngleFilter(wrapsAtFullTurn = false)
        filter.update(degrees = 90f, elapsed = Duration.ZERO)

        val shown = filter.update(degrees = -90f, elapsed = FRAME_INTERVAL)
        assertTrue(shown < 90f)
        assertTrue(shown > -90f)
    }

    @Test
    fun rollAcrossTheBoundary_takesTheShortTurn() {
        val filter = LevelAngleFilter(wrapsAtFullTurn = true)
        filter.update(degrees = 179f, elapsed = Duration.ZERO)

        val shown = filter.update(degrees = -179f, elapsed = FRAME_INTERVAL)
        assertTrue(abs(shown) > 178f)
    }

    @Test
    fun steadyInput_settlesAndAllowsFrameUpdatesToStop() {
        val filter = LevelAngleFilter(wrapsAtFullTurn = false)
        filter.update(degrees = 20f, elapsed = Duration.ZERO)

        var shown = 20f
        repeat(120) {
            shown = filter.update(degrees = 30f, elapsed = FRAME_INTERVAL)
        }

        assertEquals(30f, shown)
        assertTrue(filter.isSettled)
    }

    @Test
    fun afterSettling_smallMovementUsesTheLowSpeedFilterAgain() {
        val filter = LevelAngleFilter(wrapsAtFullTurn = true)
        filter.update(degrees = 0f, elapsed = Duration.ZERO)

        do {
            filter.update(degrees = 30f, elapsed = FRAME_INTERVAL)
        } while (!filter.isSettled)

        val shown = filter.update(degrees = 30.6f, elapsed = FRAME_INTERVAL)
        assertTrue(abs(shown - 30f) < 0.15f)
    }

    private companion object {
        private val FRAME_INTERVAL = 16.milliseconds
    }
}
