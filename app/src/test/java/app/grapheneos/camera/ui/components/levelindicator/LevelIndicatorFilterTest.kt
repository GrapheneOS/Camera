package app.grapheneos.camera.ui.components.levelindicator

import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelIndicatorFilterTest {

    @Test
    fun horizon_snapsNearLevelFromEitherSide() {
        listOf(-1f, 1f).forEach { direction ->
            val filter = LevelIndicatorFilter(mode = LevelIndicatorMode.Horizon)
            val angles = filter.update(
                roll = 0.4f * direction,
                pitch = 0.8f * direction,
                elapsed = Duration.ZERO,
            )

            assertEquals(LevelIndicatorFilter.Angles(roll = 0f, pitch = 0f), angles)
        }
    }

    @Test
    fun smallTurnOutOfTheHorizonLevelZone_doesNotGetStuckAtTheCenter() {
        val filter = LevelIndicatorFilter(mode = LevelIndicatorMode.Horizon)
        var angles = filter.update(roll = 0f, pitch = 0f, elapsed = Duration.ZERO)

        repeat(30) {
            angles = filter.update(roll = 0.6f, pitch = 1.2f, elapsed = FRAME_INTERVAL)
        }

        assertEquals(0.6f, angles.roll, 0.01f)
        assertEquals(1.2f, angles.pitch, 0.01f)
        assertTrue(filter.isSettled)
    }

    @Test
    fun verticalAlignment_snapsOnlyVeryCloseToTheCenter() {
        listOf(-1f, 1f).forEach { direction ->
            val filter = LevelIndicatorFilter(mode = LevelIndicatorMode.Crosshairs)
            var angles = filter.update(
                roll = 0f,
                pitch = 89.9f * direction,
                elapsed = Duration.ZERO,
            )
            assertEquals(90f * direction, angles.pitch)

            repeat(30) {
                angles = filter.update(
                    roll = 0f,
                    pitch = 89.5f * direction,
                    elapsed = FRAME_INTERVAL,
                )
            }

            assertEquals(89.5f * direction, angles.pitch, 0.01f)
            assertTrue(filter.isSettled)
        }
    }

    @Test
    fun handTremorNearVertical_doesNotMoveTheCrosshairAsItsDirectionChanges() {
        val filter = LevelIndicatorFilter(mode = LevelIndicatorMode.Crosshairs)
        val initial = filterAtPosition(filter = filter, horizontal = 0.2f, vertical = 0.4f)

        repeat(120) { index ->
            val noise = when (index % 2) {
                0 -> 0.1f
                else -> -0.1f
            }
            val angles = filterAtPosition(
                filter = filter,
                horizontal = 0.2f + noise,
                vertical = 0.4f - noise,
            )

            assertEquals(initial, angles)
            assertTrue(filter.isSettled)
        }
    }

    @Test
    fun crosshairsTurningAcrossTheDirectionBoundary_takeTheDirectPath() {
        val filter = LevelIndicatorFilter(mode = LevelIndicatorMode.Crosshairs)
        filter.update(roll = 170f, pitch = 80f, elapsed = Duration.ZERO)

        val angles = filter.update(roll = -170f, pitch = 80f, elapsed = FRAME_INTERVAL)

        assertTrue(abs(angles.roll) > 170f)
        assertTrue(angles.pitch >= 80f)
        assertTrue(angles.pitch < 81f)
    }

    private fun filterAtPosition(
        filter: LevelIndicatorFilter,
        horizontal: Float,
        vertical: Float,
    ): LevelIndicatorFilter.Angles {
        return filter.update(
            roll = Math.toDegrees(atan2(horizontal, vertical).toDouble()).toFloat(),
            pitch = 90f - hypot(horizontal, vertical),
            elapsed = FRAME_INTERVAL,
        )
    }

    private companion object {
        private val FRAME_INTERVAL = 16.milliseconds
    }
}
