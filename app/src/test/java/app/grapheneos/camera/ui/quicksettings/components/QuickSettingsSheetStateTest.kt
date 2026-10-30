package app.grapheneos.camera.ui.quicksettings.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingsSheetStateTest {

    private val closedSheet = sheetState(isShown = false)
    private val openSheet = sheetState(isShown = true)

    @Test
    fun initialState_followsWhetherTheSheetIsShown() {
        assertFalse(closedSheet.isShown())
        assertTrue(closedSheet.isHidden)
        assertEquals(HEIGHT, closedSheet.offset())
        assertTrue(openSheet.isShown())
        assertFalse(openSheet.isHidden)
        assertEquals(0f, openSheet.offset())
    }

    @Test
    fun drag_movesTheSheetWithTheFingerWithinItsHeight() {
        closedSheet.startDrag()

        assertEquals(-100f, closedSheet.drag(-100f))
        assertEquals(0.25f, closedSheet.targetPosition())
        assertEquals(-300f, closedSheet.drag(-500f))
        assertEquals(1f, closedSheet.targetPosition())
        assertEquals(0f, closedSheet.drag(-100f))
    }

    @Test
    fun drag_beforeLayout_movesNothing() {
        val unlaid = QuickSettingsSheetState(settleVelocity = SETTLE_VELOCITY)

        unlaid.startDrag()

        assertEquals(0f, unlaid.drag(-100f))
        assertEquals(0f, unlaid.targetPosition())
    }

    @Test
    fun slowRelease_settlesOnTheNearerSide() {
        closedSheet.startDrag()
        closedSheet.drag(-200f)

        assertTrue(closedSheet.release(velocity = 0f))
        assertFalse(closedSheet.isDragging)

        openSheet.startDrag()
        openSheet.drag(201f)

        assertFalse(openSheet.release(velocity = 0f))
    }

    @Test
    fun fling_settlesInItsDirectionWhateverTheDistance() {
        closedSheet.startDrag()
        closedSheet.drag(-10f)

        assertTrue(closedSheet.release(velocity = -SETTLE_VELOCITY))

        openSheet.startDrag()
        openSheet.drag(10f)

        assertFalse(openSheet.release(velocity = SETTLE_VELOCITY))
    }

    @Test
    fun show_duringADrag_leavesItToTheRelease() {
        closedSheet.startDrag()
        closedSheet.show(true)

        assertFalse(closedSheet.isShown())
        assertFalse(closedSheet.release(velocity = 0f))

        closedSheet.show(true)

        assertTrue(closedSheet.isShown())
        assertEquals(1f, closedSheet.targetPosition())
    }

    private fun sheetState(isShown: Boolean): QuickSettingsSheetState {
        return QuickSettingsSheetState(
            settleVelocity = SETTLE_VELOCITY,
            isShown = isShown,
        ).also { it.updateHeight(height = HEIGHT) }
    }

    private companion object {
        private const val HEIGHT = 400f
        private const val SETTLE_VELOCITY = 125f
    }
}
