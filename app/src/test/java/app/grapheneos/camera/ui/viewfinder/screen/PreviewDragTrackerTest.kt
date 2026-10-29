package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.ui.viewfinder.screen.PreviewDragTracker.VerticalDrag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewDragTrackerTest {

    private val tracker = PreviewDragTracker(touchSlop = TOUCH_SLOP)

    @Test
    fun movementWithinTheTouchSlop_isNoDrag() {
        tracker.down(x = 0f, y = 0f, canDragVertically = true)

        assertNull(tracker.move(x = 0f, y = TOUCH_SLOP))
        assertFalse(tracker.isDraggingVertically)
    }

    @Test
    fun verticalMovement_startsADragFromTheEdgeOfTheTouchSlop() {
        tracker.down(x = 0f, y = 100f, canDragVertically = true)

        assertEquals(VerticalDrag.Started(delta = -20f), tracker.move(x = 0f, y = 70f))
        assertEquals(VerticalDrag.Moved(delta = -5f), tracker.move(x = 0f, y = 65f))
        assertTrue(tracker.isDraggingVertically)
    }

    @Test
    fun sidewaysMovement_isLeftToTheSwipes() {
        tracker.down(x = 0f, y = 0f, canDragVertically = true)

        assertNull(tracker.move(x = 30f, y = 20f))
        assertNull(tracker.move(x = 30f, y = 80f))
        assertFalse(tracker.isDraggingVertically)
    }

    @Test
    fun verticalMovement_whileThePanelIsUnavailable_isNoDrag() {
        tracker.down(x = 0f, y = 0f, canDragVertically = false)

        assertNull(tracker.move(x = 0f, y = 80f))
        assertFalse(tracker.isDraggingVertically)
    }

    @Test
    fun stop_reportsWhetherADragWasStopped() {
        tracker.down(x = 0f, y = 0f, canDragVertically = true)
        tracker.move(x = 0f, y = 80f)

        assertTrue(tracker.stop())
        assertFalse(tracker.stop())
        assertNull(tracker.move(x = 0f, y = 120f))
    }

    private companion object {
        private const val TOUCH_SLOP = 10f
    }
}
