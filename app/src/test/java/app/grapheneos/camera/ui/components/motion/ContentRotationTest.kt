package app.grapheneos.camera.ui.components.motion

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
class ContentRotationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var degrees by mutableFloatStateOf(0f)
    private lateinit var rotation: () -> Float

    @Test
    fun withoutAProvider_contentIsNotTurned() {
        composeRule.setContent {
            rotation = LocalContentRotation.current
        }

        assertEquals(0f, rotation())
    }

    @Test
    fun aProvider_startsAtItsAngle() {
        degrees = QUARTER_TURN
        setContent()

        assertEquals(QUARTER_TURN, rotation())
    }

    @Test
    fun aNewAngle_isReachedTheShortWay() {
        setContent()

        degrees = THREE_QUARTER_TURN
        composeRule.waitForIdle()

        assertEquals(-QUARTER_TURN, rotation())
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
            ProvideContentRotation(degrees = degrees) {
                rotation = LocalContentRotation.current
            }
        }
    }

    private companion object {
        private const val QUARTER_TURN = 90f
        private const val THREE_QUARTER_TURN = 270f
    }
}
