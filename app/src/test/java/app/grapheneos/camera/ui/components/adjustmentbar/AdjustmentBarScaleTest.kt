package app.grapheneos.camera.ui.components.adjustmentbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentBarScaleTest {

    private val scale = AdjustmentBarScale(
        valueRange = -12f..12f,
        steps = 23,
        majorTickInterval = 4,
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
    fun value_isTheValueAtThePosition() {
        assertEquals(-12f, scale.value(position = 0f))
        assertEquals(0f, scale.value(position = 12f))
        assertEquals(12f, scale.value(position = 24f))
    }

    @Test
    fun nearestTick_staysOnTheScale() {
        assertEquals(3, scale.nearestTick(position = 3.4f))
        assertEquals(0, scale.nearestTick(position = -1f))
    }

    @Test
    fun scale_withoutIntermediateSteps_usesOnlyTheTwoEnds() {
        val scale = AdjustmentBarScale(
            valueRange = 0f..1f,
            steps = 0,
            majorTickInterval = 4,
        )

        assertEquals(1, scale.lastTick)
        assertEquals(0f, scale.value(position = 0f))
        assertEquals(1f, scale.value(position = 1f))
        assertEquals(0, scale.tickOf(value = 0.4f))
        assertEquals(1, scale.tickOf(value = 0.6f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun scale_withNegativeSteps_isRejected() {
        AdjustmentBarScale(
            valueRange = 0f..1f,
            steps = -1,
            majorTickInterval = 4,
        )
    }

    @Test
    fun isMajor_everyIntervalFromTheStart() {
        assertTrue(scale.isMajor(tick = 0))
        assertTrue(scale.isMajor(tick = 12))
        assertFalse(scale.isMajor(tick = 13))
    }

    @Test(expected = IllegalArgumentException::class)
    fun scale_withoutMajorTicks_isRejected() {
        AdjustmentBarScale(
            valueRange = 0f..1f,
            steps = 4,
            majorTickInterval = 0,
        )
    }
}
