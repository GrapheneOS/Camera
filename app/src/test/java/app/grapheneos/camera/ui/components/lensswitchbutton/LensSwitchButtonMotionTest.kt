package app.grapheneos.camera.ui.components.lensswitchbutton

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LensSwitchButtonMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var flipped by mutableStateOf(true)
    private lateinit var turn: State<Float>

    @Test
    fun animateFlipTurn_firstComposition_staysStill() {
        setContent()

        assertEquals(0f, turn.value)
    }

    @Test
    fun animateFlipTurn_everyChange_turnsHalfCounterclockwise() {
        setContent()

        flipped = false
        composeRule.waitForIdle()
        assertEquals(-180f, turn.value)

        flipped = true
        composeRule.waitForIdle()
        assertEquals(-360f, turn.value)
    }

    @Test
    fun animateFlipTurn_changeDuringATurn_keepsTurningTheSameWay() {
        setContent()
        composeRule.mainClock.autoAdvance = false

        flipped = false
        composeRule.mainClock.advanceTimeBy(MID_TURN_MILLIS)
        flipped = true
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(-360f, turn.value)
    }

    private fun setContent() {
        composeRule.setContent {
            turn = animateFlipTurn(flipped = flipped)
        }
    }

    private companion object {
        private const val MID_TURN_MILLIS = 100L
    }
}
