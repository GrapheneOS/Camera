package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsPanelStateTest {

    private val state = panelState(isShown = false)

    @Test
    fun initialState_followsWhetherTheDetailsAreShown() {
        assertFalse(state.isShown())
        assertTrue(panelState(isShown = true).isShown())
    }

    @Test
    fun openPanel_startsUnderTheCornersOfAFourByThreeWindow() {
        assertEquals(0f, state.panelShift(progress = 1f))
        assertEquals(SCREEN.height - OPEN_PANEL_TOP, state.panelHeight())
    }

    @Test
    fun closedPanel_isJustBelowTheScreen() {
        assertEquals(SCREEN.height, OPEN_PANEL_TOP + state.panelShift(progress = 0f))
    }

    @Test
    fun photoShift_centersThePhotoInTheWindowAboveThePanel() {
        assertEquals(675f / 2f - SCREEN.height / 2f, state.photoShift(progress = 1f))
        assertEquals(0f, state.photoShift(progress = 0f))
    }

    @Test
    fun drag_movesThePanelWithTheFinger() {
        state.startDrag()
        state.drag(panChange = -TRAVEL / 2f)

        assertEquals(0.5f, state.progress)
    }

    @Test
    fun drag_staysWithinClosedAndOpen() {
        state.startDrag()
        state.drag(panChange = -TRAVEL * 2f)

        assertEquals(1f, state.progress)

        state.drag(panChange = TRAVEL * 3f)

        assertEquals(0f, state.progress)
    }

    @Test
    fun release_afterAFlingUp_opens() {
        state.startDrag()
        state.drag(panChange = -10f)

        assertTrue(state.release(velocity = -SETTLE_VELOCITY))
        assertEquals(1f, state.progress)
        assertFalse(state.isDragging)
    }

    @Test
    fun release_afterAFlingDown_closes() {
        state.show(isShown = true)
        state.startDrag()
        state.drag(panChange = 10f)

        assertFalse(state.release(velocity = SETTLE_VELOCITY))
        assertEquals(0f, state.progress)
    }

    @Test
    fun release_withoutAFling_settlesOnTheNearerEnd() {
        state.startDrag()
        state.drag(panChange = -TRAVEL / 4f)

        assertFalse(state.release(velocity = 0f))

        state.startDrag()
        state.drag(panChange = -TRAVEL * 3f / 4f)

        assertTrue(state.release(velocity = 0f))
    }

    @Test
    fun release_afterOpeningAndComingBack_closes() {
        state.startDrag()
        state.drag(panChange = -TRAVEL / 2f)
        state.drag(panChange = TRAVEL / 2f)

        assertFalse(state.release(velocity = -1f))
        assertFalse(state.isShown())
    }

    @Test
    fun show_whileDragging_leavesTheFingerInCharge() {
        state.startDrag()
        state.drag(panChange = -10f)

        val dragged = state.progress

        state.show(isShown = false)

        assertEquals(dragged, state.progress)
    }

    @Test
    fun show_opensAndClosesThePanel() {
        state.show(isShown = true)

        assertEquals(1f, state.progress)

        state.show(isShown = false)

        assertEquals(0f, state.progress)
    }

    private fun panelState(isShown: Boolean): DetailsPanelState {
        return DetailsPanelState(
            cornerRadius = CORNER_RADIUS,
            settleVelocity = SETTLE_VELOCITY,
            isShown = isShown,
        ).apply {
            updateLayout(SCREEN)
        }
    }

    private companion object {
        const val CORNER_RADIUS = 60f
        const val SETTLE_VELOCITY = 300f
        const val OPEN_PANEL_TOP = 675f - CORNER_RADIUS
        val SCREEN = Size(width = 900f, height = 2000f)
        val TRAVEL = SCREEN.height - OPEN_PANEL_TOP
    }
}
