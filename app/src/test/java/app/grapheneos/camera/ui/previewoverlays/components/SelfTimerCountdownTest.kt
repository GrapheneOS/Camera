package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.grapheneos.camera.ui.core.theme.CameraTheme
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelfTimerCountdownTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun remainingTime_isRoundedUpToWholeSeconds() {
        var remaining by mutableStateOf(3.seconds)
        composeRule.setContent {
            CameraTheme {
                SelfTimerCountdown(
                    remaining = remaining,
                    modifier = Modifier.testTag(TAG),
                )
            }
        }

        composeRule.onNodeWithTag(TAG).assertTextEquals("3")

        remaining = 1500.milliseconds
        composeRule.onNodeWithTag(TAG).assertTextEquals("2")

        remaining = 1.seconds + 1.nanoseconds
        composeRule.onNodeWithTag(TAG).assertTextEquals("2")

        remaining = 1.nanoseconds
        composeRule.onNodeWithTag(TAG).assertTextEquals("1")
    }

    private companion object {
        private const val TAG = "timer"
    }
}
