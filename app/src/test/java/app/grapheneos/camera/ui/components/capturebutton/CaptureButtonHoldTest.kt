package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonDirection
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonHoldEnd
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureButtonHoldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fixture = CaptureButtonFixture(composeRule = composeRule)

    @Before
    fun enableHold() {
        fixture.isHoldEnabled = true
    }

    @Test
    fun captureButton_held_startsAHoldInsteadOfClicking() {
        fixture.setContent()

        fixture.hold()

        assertEquals(1, fixture.holdStarts)
        assertEquals(0, fixture.clicks)
        assertTrue(fixture.holdEnds.isEmpty())
    }

    @Test
    fun captureButton_releasedAfterHold_endsTheHoldAsReleased() {
        fixture.setContent()

        fixture.hold()
        fixture.button().performTouchInput { up() }

        assertEquals(listOf(CaptureButtonHoldEnd.Released), fixture.holdEnds)
        assertEquals(0, fixture.clicks)
    }

    @Test
    fun captureButton_tappedWithHoldEnabled_stillClicks() {
        fixture.setContent()

        fixture.button().performTouchInput {
            down(center)
            up()
        }

        assertEquals(1, fixture.clicks)
        assertEquals(0, fixture.holdStarts)
    }

    @Test
    fun captureButton_heldWithoutHoldEnabled_clicksOnRelease() {
        fixture.isHoldEnabled = false
        fixture.setContent()

        fixture.hold()
        fixture.button().performTouchInput { up() }

        assertEquals(1, fixture.clicks)
    }

    @Test
    fun captureButton_draggedUpWhileHeld_reportsOnlyTheVerticalDrag() {
        fixture.setContent()

        fixture.hold()
        fixture.button().performTouchInput { moveBy(Offset(x = 0f, y = -200f)) }

        assertEquals(0f, fixture.drag.x)
        assertTrue(fixture.drag.y < 0f)
    }

    @Test
    fun captureButton_recomposedWhileHeld_keepsTheHold() {
        fixture.setContent()

        fixture.hold()
        fixture.tone = CaptureButtonTone.Recording
        composeRule.waitForIdle()
        fixture.button().performTouchInput { up() }

        assertEquals(listOf(CaptureButtonHoldEnd.Released), fixture.holdEnds)
    }

    @Test
    fun captureButton_releasedAtATarget_commitsIt() {
        fixture.holdTargets = listOf(LOCK)
        fixture.setContent()

        fixture.hold()
        fixture.button().performTouchInput {
            moveBy(Offset(x = -LOCK.distance.toPx() - SLACK, y = 0f))
            up()
        }

        assertEquals(listOf(CaptureButtonHoldEnd.Committed(target = LOCK)), fixture.holdEnds)
    }

    @Test
    fun captureButton_draggedBackFromATarget_releasesWithoutCommitting() {
        fixture.holdTargets = listOf(LOCK)
        fixture.setContent()

        fixture.hold()
        fixture.button().performTouchInput {
            moveBy(Offset(x = -LOCK.distance.toPx() - SLACK, y = 0f))
            moveBy(Offset(x = LOCK.distance.toPx(), y = 0f))
            up()
        }

        assertEquals(listOf(CaptureButtonHoldEnd.Released), fixture.holdEnds)
    }

    @Test
    fun captureButton_heldAtATarget_showsItArmedUntilReleased() {
        fixture.holdTargets = listOf(LOCK)
        fixture.setContent()

        fixture.hold()
        fixture.button().performTouchInput {
            moveBy(Offset(x = -LOCK.distance.toPx() - SLACK, y = 0f))
        }
        assertEquals(LOCK, fixture.holdState.armedTarget)

        fixture.button().performTouchInput { up() }
        assertNull(fixture.holdState.armedTarget)
        assertFalse(fixture.holdState.isHeld)
    }

    @Test
    fun captureButton_targetAccessibilityAction_commitsTheTarget() {
        fixture.holdTargets = listOf(LOCK)
        fixture.setContent()

        fixture.button()
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .single { it.label == LOCK.accessibilityLabel }
            .action()

        assertEquals(1, fixture.holdStarts)
        assertEquals(listOf(CaptureButtonHoldEnd.Committed(target = LOCK)), fixture.holdEnds)
    }

    @Test
    fun captureButton_draggedDiagonallyToATarget_zoomsAndCommits() {
        fixture.holdTargets = listOf(LOCK)
        fixture.setContent()

        fixture.hold()
        fixture.button().performTouchInput {
            moveBy(Offset(x = -LOCK.distance.toPx() - SLACK, y = -LOCK.distance.toPx()))
            up()
        }

        assertTrue(fixture.drag.y < 0f)
        assertEquals(listOf(CaptureButtonHoldEnd.Committed(target = LOCK)), fixture.holdEnds)
    }

    @Test
    fun captureButton_swipedStraightToATarget_commitsWithoutWaitingForTheHold() {
        fixture.holdTargets = listOf(LOCK)
        fixture.setContent()

        fixture.button().performTouchInput {
            down(center)
            moveBy(Offset(x = -LOCK.distance.toPx() - SLACK, y = 0f))
            up()
        }

        assertEquals(1, fixture.holdStarts)
        assertEquals(listOf(CaptureButtonHoldEnd.Committed(target = LOCK)), fixture.holdEnds)
    }

    @Test
    fun captureButton_swipedAwayFromTargets_neitherClicksNorHolds() {
        fixture.holdTargets = listOf(LOCK)
        fixture.setContent()

        fixture.button().performTouchInput {
            down(center)
            moveBy(Offset(x = width * 2f, y = 0f))
            up()
        }

        assertEquals(0, fixture.clicks)
        assertEquals(0, fixture.holdStarts)
    }

    @Test
    fun captureButton_removedWhileHeld_endsTheHoldAsCancelled() {
        fixture.setContent()

        fixture.hold()
        fixture.isShown = false
        composeRule.waitForIdle()

        assertEquals(listOf(CaptureButtonHoldEnd.Cancelled), fixture.holdEnds)
    }

    private companion object {
        private const val SLACK = 20f

        private val LOCK = CaptureButtonTarget(
            direction = CaptureButtonDirection.Start,
            icon = TEST_ICON,
            accessibilityLabel = "Lock recording",
            distance = 100.dp,
        )
    }
}
