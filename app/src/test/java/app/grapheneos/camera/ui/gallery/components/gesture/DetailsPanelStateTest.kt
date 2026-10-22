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
    fun beforeLayout_nothingIsLaidOut() {
        val unlaid = DetailsPanelState(
            cornerRadius = CORNER_RADIUS,
            settleVelocity = SETTLE_VELOCITY,
        )

        assertFalse(unlaid.isLaidOut())
        assertTrue(state.isLaidOut())
    }

    @Test
    fun positions_placeThePanelBelowTheScreenUnderTheWindowAndAtTheTop() {
        assertEquals(SCREEN.height, state.topAt(position = 0f))
        assertEquals(OPEN_TOP, state.topAt(position = 1f))
        assertEquals(0f, state.topAt(position = 2f))
    }

    @Test
    fun photoShift_centersThePhotoInTheWindowAndStaysWhileExpanding() {
        val centered = 675f / 2f - SCREEN.height / 2f

        assertEquals(0f, state.photoShiftAt(position = 0f))
        assertEquals(centered, state.photoShiftAt(position = 1f))
        assertEquals(centered, state.photoShiftAt(position = 2f))
    }

    @Test
    fun openingAndExpansion_splitThePosition() {
        assertEquals(0.5f, state.openingAt(position = 0.5f))
        assertEquals(1f, state.openingAt(position = 1.5f))
        assertEquals(0f, state.expansionAt(position = 1f))
        assertEquals(0.5f, state.expansionAt(position = 1.5f))
    }

    @Test
    fun backButton_fadesInWhileOpeningAndOutWhileExpanding() {
        assertEquals(0f, state.backButtonVisibilityAt(position = 0f))
        assertEquals(1f, state.backButtonVisibilityAt(position = 1f))
        assertEquals(0.5f, state.backButtonVisibilityAt(position = 1.5f))
        assertEquals(0f, state.backButtonVisibilityAt(position = 2f))
    }

    @Test
    fun drag_movesThePanelWithTheFinger() {
        state.startDrag()
        state.drag(panChange = -OPEN_TRAVEL / 2f)

        assertEquals(0.5f, state.targetPosition())
    }

    @Test
    fun drag_ofContentThatFits_stopsAtTheOpenPanel() {
        state.startDrag()

        val consumed = state.drag(panChange = -SCREEN.height)

        assertEquals(1f, state.targetPosition())
        assertEquals(-OPEN_TRAVEL, consumed)
    }

    @Test
    fun drag_ofContentThatDoesNotFit_expandsToTheTop() {
        state.updateContentHeight(SCREEN.height)
        state.startDrag()
        state.drag(panChange = -SCREEN.height * 2f)

        assertEquals(2f, state.targetPosition())
    }

    @Test
    fun release_afterAFlingUpFromClosed_opens() {
        state.updateContentHeight(SCREEN.height)
        state.startDrag()
        state.drag(panChange = -10f)

        assertTrue(state.release(velocity = -SETTLE_VELOCITY))
        assertEquals(DetailsAnchor.Open, state.anchor)
        assertFalse(state.isDragging)
    }

    @Test
    fun release_afterAFlingUpFromOpen_expandsWhenTheContentDoesNotFit() {
        state.updateContentHeight(SCREEN.height)
        state.show(isShown = true)
        state.startDrag()
        state.drag(panChange = -10f)
        state.release(velocity = -SETTLE_VELOCITY)

        assertEquals(DetailsAnchor.Expanded, state.anchor)
    }

    @Test
    fun release_afterAFlingDownFromExpanded_opens() {
        expand()

        state.startDrag()
        state.drag(panChange = 10f)

        assertTrue(state.release(velocity = SETTLE_VELOCITY))
        assertEquals(DetailsAnchor.Open, state.anchor)
    }

    @Test
    fun release_afterAFlingDownFromOpen_closes() {
        state.show(isShown = true)
        state.startDrag()
        state.drag(panChange = 10f)

        assertFalse(state.release(velocity = SETTLE_VELOCITY))
        assertEquals(DetailsAnchor.Closed, state.anchor)
    }

    @Test
    fun release_withoutAFling_settlesOnTheNearestPosition() {
        state.startDrag()
        state.drag(panChange = -OPEN_TRAVEL / 4f)

        assertFalse(state.release(velocity = 0f))

        state.startDrag()
        state.drag(panChange = -OPEN_TRAVEL * 3f / 4f)

        assertTrue(state.release(velocity = 0f))
    }

    @Test
    fun release_afterOpeningAndComingBack_closes() {
        state.startDrag()
        state.drag(panChange = -OPEN_TRAVEL / 2f)
        state.drag(panChange = OPEN_TRAVEL / 2f)

        assertFalse(state.release(velocity = -1f))
        assertFalse(state.isShown())
    }

    @Test
    fun cancelDrag_keepsThePanelWhereItWas() {
        state.show(isShown = true)
        state.startDrag()
        state.cancelDrag()

        assertFalse(state.isDragging)
        assertEquals(DetailsAnchor.Open, state.anchor)
    }

    @Test
    fun show_whileDragging_leavesTheFingerInCharge() {
        state.startDrag()
        state.drag(panChange = -10f)

        val dragged = state.targetPosition()

        state.show(isShown = false)

        assertEquals(dragged, state.targetPosition())
    }

    @Test
    fun show_keepsAnExpandedPanelExpandedAndClosesIt() {
        expand()

        state.show(isShown = true)
        assertEquals(DetailsAnchor.Expanded, state.anchor)
        state.show(isShown = false)

        assertEquals(DetailsAnchor.Closed, state.anchor)
    }

    @Test
    fun shorterContent_leavesAnExpandedPanelWhereItIs() {
        expand()

        state.updateContentHeight(0f)

        assertEquals(DetailsAnchor.Expanded, state.anchor)
        assertEquals(2f, state.targetPosition())
    }

    @Test
    fun shorterContent_stillLetsAnExpandedPanelBeDraggedBackUp() {
        expand()

        state.updateContentHeight(0f)
        state.startDrag()
        state.drag(panChange = 100f)
        state.drag(panChange = -100f)

        assertEquals(2f, state.targetPosition())
    }

    private fun expand() {
        state.updateContentHeight(SCREEN.height)
        state.show(isShown = true)
        state.startDrag()
        state.drag(panChange = -SCREEN.height)
        state.release(velocity = 0f)
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
        const val OPEN_TOP = 675f - CORNER_RADIUS
        val SCREEN = Size(width = 900f, height = 2000f)
        val OPEN_TRAVEL = SCREEN.height - OPEN_TOP
    }
}
