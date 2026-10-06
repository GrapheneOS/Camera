package app.grapheneos.camera.ui.components.timerchip

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.grapheneos.camera.ui.core.CameraTheme
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimerChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun timerChip_readsItsLabelWithTheTime() {
        composeRule.setContent {
            CameraTheme {
                TimerChip(
                    elapsed = 1.seconds,
                    label = LABEL,
                )
            }
        }

        composeRule.onNodeWithText(LABEL)
            .assertIsDisplayed()
            .assert(hasText(TIME))
    }

    private companion object {
        private const val LABEL = "PAUSED"
        private const val TIME = "00:01"
    }
}
