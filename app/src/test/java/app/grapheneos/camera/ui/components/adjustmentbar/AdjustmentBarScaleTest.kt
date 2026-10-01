package app.grapheneos.camera.ui.components.adjustmentbar

import org.junit.Assert.assertEquals
import org.junit.Test

class AdjustmentBarScaleTest {

    private val scale = AdjustmentBarScale(
        valueRange = -12f..12f,
        steps = 23,
    )

    @Test
    fun lastTick_countsTheEnds() {
        assertEquals(24, scale.lastTick)
    }

    @Test
    fun tickOf_roundsToTheNearestStep() {
        assertEquals(12, scale.tickOf(value = 0f))
        assertEquals(13, scale.tickOf(value = 0.6f))
    }

    @Test
    fun tickOf_clampsToTheRange() {
        assertEquals(0, scale.tickOf(value = -20f))
        assertEquals(24, scale.tickOf(value = 20f))
    }

    @Test
    fun value_isTheValueOfTheTick() {
        assertEquals(-12f, scale.value(tick = 0))
        assertEquals(0f, scale.value(tick = 12))
        assertEquals(12f, scale.value(tick = 24))
    }

    @Test
    fun nearestTick_staysOnTheScale() {
        assertEquals(3, scale.nearestTick(position = 3.4f))
        assertEquals(0, scale.nearestTick(position = -1f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun scale_withoutSteps_isRejected() {
        AdjustmentBarScale(
            valueRange = 0f..1f,
            steps = 0,
        )
    }
}
