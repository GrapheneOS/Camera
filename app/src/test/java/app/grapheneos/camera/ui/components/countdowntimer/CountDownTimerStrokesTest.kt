package app.grapheneos.camera.ui.components.countdowntimer

import org.junit.Assert.assertEquals
import org.junit.Test

class CountDownTimerStrokesTest {

    @Test
    fun step_followsTheBoldness() {
        assertEquals(50, CountDownTimerStrokes.step(boldness = 0f))
        assertEquals(150, CountDownTimerStrokes.step(boldness = 1f))
    }

    @Test
    fun step_staysWithinTheOvershoot() {
        assertEquals(1, CountDownTimerStrokes.step(boldness = -1f))
        assertEquals(170, CountDownTimerStrokes.step(boldness = 2f))
    }
}
