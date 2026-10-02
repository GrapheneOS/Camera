package app.grapheneos.camera.ui.components.adjustmentbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentBarTintTest {

    @Test
    fun strength_inTheMiddle_isZero() {
        assertEquals(0f, strength(position = 12f))
    }

    @Test
    fun strength_atTheEnds_isFull() {
        assertEquals(1f, strength(position = 0f))
        assertEquals(1f, strength(position = 24f))
    }

    @Test
    fun strength_isTheSameOnBothSides() {
        assertEquals(strength(position = 6f), strength(position = 18f))
    }

    @Test
    fun strength_startsFlat() {
        assertTrue(strength(position = 12.6f) < FLAT_START)
    }

    private fun strength(position: Float): Float {
        return AdjustmentBarTint.strength(
            position = position,
            middle = MIDDLE,
        )
    }

    private companion object {
        private const val MIDDLE = 12f
        private const val FLAT_START = 0.01f
    }
}
