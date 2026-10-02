package app.grapheneos.camera.ui.components.zoom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomBarScaleTest {

    private val scale = ZoomBarScale(
        valueRange = 0.5f..10f,
        stops = listOf(1f, 2f, 5f, 10f),
    )

    @Test
    fun stopTicks_giveEachSpanFiveTicksPerOctaveRounded() {
        assertEquals(listOf(5, 10, 17, 22), scale.stopTicks)
        assertEquals(22, scale.lastTick)
    }

    @Test
    fun position_putsEveryStopExactlyOnItsTick() {
        assertEquals(10f, scale.position(value = 2f), TOLERANCE)
        assertEquals(17f, scale.position(value = 5f), TOLERANCE)
        assertEquals(22f, scale.position(value = 10f), TOLERANCE)
    }

    @Test
    fun ticks_areEvenWithinASpan() {
        val ratio = scale.value(position = 11f) / scale.value(position = 10f)

        assertEquals(ratio, scale.value(position = 17f) / scale.value(position = 16f), TOLERANCE)
    }

    @Test
    fun value_isTheInverseOfPosition() {
        assertEquals(2.7f, scale.value(position = scale.position(value = 2.7f)), TOLERANCE)
        assertEquals(7.3f, scale.value(position = scale.position(value = 7.3f)), TOLERANCE)
    }

    @Test
    fun position_andValue_clampToTheRange() {
        assertEquals(0f, scale.position(value = 0.1f), TOLERANCE)
        assertEquals(22f, scale.position(value = 30f), TOLERANCE)
        assertEquals(10f, scale.value(position = 40f), TOLERANCE)
    }

    @Test
    fun isMajor_onlyOnStops() {
        assertTrue(scale.isMajor(tick = 17))
        assertFalse(scale.isMajor(tick = 16))
        assertFalse(scale.isMajor(tick = 0))
    }

    @Test
    fun position_andValue_areExactOnStops() {
        assertEquals(17f, scale.position(value = 5f))
        assertEquals(5f, scale.value(position = 17f))
    }

    @Test
    fun isStop_onlyExactlyOnAStop() {
        assertTrue(scale.isStop(position = 17f))
        assertFalse(scale.isStop(position = 16.9f))
        assertFalse(scale.isStop(position = 16f))
    }

    @Test
    fun firstStop_inBothDirections() {
        assertEquals(17f, scale.firstStop(from = 16.6f, to = 17f))
        assertEquals(17f, scale.firstStop(from = 17.4f, to = 17f))
        assertEquals(10f, scale.firstStop(from = 9f, to = 19f))
        assertEquals(17f, scale.firstStop(from = 19f, to = 9f))
    }

    @Test
    fun firstStop_leavesOutTheStopItStartsOn() {
        assertNull(scale.firstStop(from = 17f, to = 17.4f))
        assertNull(scale.firstStop(from = 18f, to = 19f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun scale_withStopsOutOfOrder_isRejected() {
        ZoomBarScale(
            valueRange = 0.5f..8f,
            stops = listOf(2f, 1f),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun scale_withAStopOutsideTheRange_isRejected() {
        ZoomBarScale(
            valueRange = 1f..8f,
            stops = listOf(0.5f, 1f),
        )
    }

    private companion object {
        private const val TOLERANCE = 0.0001f
    }
}
