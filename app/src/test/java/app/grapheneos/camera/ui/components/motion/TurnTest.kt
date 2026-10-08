package app.grapheneos.camera.ui.components.motion

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TurnTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var degrees by mutableFloatStateOf(0f)
    private lateinit var turn: State<Float>

    @Test
    fun animateTurn_turnedPastHalfATurnAndBack_readsWithinHalfATurn() {
        setContent()

        degrees = NEAR_HALF_TURN
        composeRule.waitForIdle()
        degrees = -NEAR_HALF_TURN
        composeRule.waitForIdle()
        degrees = 0f
        composeRule.waitForIdle()

        assertEquals(0f, turn.value)
    }

    @Test
    fun shortestTurn_threeQuarters_turnsBackInstead() {
        assertEquals(-90f, shortestTurn(from = 0f, to = 270f))
    }

    @Test
    fun shortestTurn_fromAnAccumulatedAngle_takesTheShortWay() {
        assertEquals(10f, shortestTurn(from = 350f, to = 0f))
    }

    @Test
    fun shortestTurn_sameAngle_staysStill() {
        assertEquals(0f, shortestTurn(from = 720f, to = 0f))
    }

    private fun setContent() {
        composeRule.setContent {
            turn = animateTurn(
                degrees = { degrees },
                animationSpec = MORPH_SPEC,
            )
        }
    }

    private companion object {
        private const val NEAR_HALF_TURN = 170f
    }
}
