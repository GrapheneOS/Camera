package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureButtonDragFilterTest {

    private val filter = CaptureButtonDragFilter(touchSlop = TOUCH_SLOP)

    @Test
    fun filter_withinTheSlop_dropsTheMovement() {
        val delta = filter.filter(Offset(x = 5f, y = -5f))

        assertEquals(Offset.Zero, delta)
    }

    @Test
    fun filter_pastTheSlopOnOneAxis_passesOnlyThatAxis() {
        filter.filter(Offset(x = 0f, y = -15f))

        val delta = filter.filter(Offset(x = 3f, y = -4f))

        assertEquals(Offset(x = 0f, y = -4f), delta)
    }

    @Test
    fun filter_pastTheSlopOnBothAxes_passesBoth() {
        filter.filter(Offset(x = 15f, y = -15f))

        val delta = filter.filter(Offset(x = 3f, y = -4f))

        assertEquals(Offset(x = 3f, y = -4f), delta)
    }

    @Test
    fun filter_backWithinTheSlop_keepsTheAxisActive() {
        filter.filter(Offset(x = 0f, y = -15f))
        filter.filter(Offset(x = 0f, y = 12f))

        val delta = filter.filter(Offset(x = 0f, y = 2f))

        assertEquals(Offset(x = 0f, y = 2f), delta)
    }

    private companion object {
        private const val TOUCH_SLOP = 10f
    }
}
