package app.grapheneos.camera.ui.components.levelindicator

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
class LevelIndicatorMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var roll by mutableFloatStateOf(0f)
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

        assertEquals(1f, motion.rollLevel)
    }

    private fun setContent() {
        composeRule.setContent {
            motion = rememberLevelIndicatorMotion(
                roll = { roll },
                pitch = { 0f },
            )
        }
    }

    private companion object {
        private const val NEAR_HALF_TURN = 170f
    }
}
