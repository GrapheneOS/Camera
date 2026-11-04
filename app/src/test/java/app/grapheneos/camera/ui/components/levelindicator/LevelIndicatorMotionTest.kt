package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LevelIndicatorMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var mode by mutableStateOf(LevelIndicatorMode.Horizon)
    private var roll by mutableFloatStateOf(0f)
    private var pitch by mutableFloatStateOf(0f)
    private lateinit var motion: LevelIndicatorMotion

    @Test
    fun rememberLevelIndicatorMotion_rollTurnedPastHalfATurnAndBack_isLevelAgain() {
        setContent()

        roll = NEAR_HALF_TURN
        composeRule.waitForIdle()
        roll = -NEAR_HALF_TURN
        composeRule.waitForIdle()
        roll = 0f
        composeRule.waitForIdle()

        assertEquals(1f, motion.rollAlignmentFraction)
    }

    @Test
    fun crosshairs_alignWhilePointingUpOrDown() {
        mode = LevelIndicatorMode.Crosshairs
        setContent()

        listOf(90f, -90f).forEach { value ->
            pitch = value
            composeRule.waitForIdle()

            assertEquals(LevelIndicatorMode.Crosshairs, motion.mode)
            assertEquals(1f, motion.verticalAlignmentFraction)
        }
    }

    @Test
    fun sensorMotion_isSmoothedAndCatchesUpWithinOneHundredMilliseconds() {
        setContent()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle {
            roll = 20f
            pitch = 15f
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertTrue(motion.roll < 20f)
        assertTrue(motion.pitch < 15f)

        composeRule.mainClock.advanceTimeBy(milliseconds = 96)
        composeRule.waitForIdle()

        assertTrue(motion.roll > 19f)
        assertTrue(motion.pitch > 14f)
    }

    @Test
    fun changingMode_restartsTheFilterAndKeepsTheModeAcrossPitchAngles() {
        setContent()

        composeRule.runOnIdle {
            mode = LevelIndicatorMode.Crosshairs
            pitch = 65f
        }
        composeRule.waitForIdle()
        assertEquals(65f, motion.pitch, 0.01f)

        listOf(45f, 70f, 80f, 90f, -70f).forEach { value ->
            pitch = value
            composeRule.waitForIdle()
            assertEquals(LevelIndicatorMode.Crosshairs, motion.mode)
            assertEquals(value, motion.pitch, 0.01f)
        }
    }

    private fun setContent() {
        composeRule.setContent {
            motion = rememberLevelIndicatorMotion(
                mode = mode,
                roll = { roll },
                pitch = { pitch },
            )
        }
    }

    private companion object {
        private const val NEAR_HALF_TURN = 170f
    }
}
