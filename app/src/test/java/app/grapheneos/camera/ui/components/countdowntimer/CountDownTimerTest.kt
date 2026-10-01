package app.grapheneos.camera.ui.components.countdowntimer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CountDownTimerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var value by mutableIntStateOf(3)

    @Test
    fun countDownTimer_showsItsValue() {
        setContent()

        composeRule.onNodeWithTag(TAG).assertTextEquals("3")
    }

    @Test
    fun countDownTimer_followsANewValue() {
        setContent()

        value = 2

        composeRule.onNodeWithTag(TAG).assertTextEquals("2")
    }

    @Test
    fun countDownTimer_announcesNewValuesPolitely() {
        setContent()

        composeRule.onNodeWithTag(TAG).assert(
            SemanticsMatcher.expectValue(
                key = SemanticsProperties.LiveRegion,
                expectedValue = LiveRegionMode.Polite,
            ),
        )
    }

    @Test
    @Config(qualifiers = "fa")
    fun countDownTimer_usesTheDigitsOfTheLocale() {
        value = 10
        setContent()

        composeRule.onNodeWithTag(TAG).assertTextEquals("۱۰")
    }

    private fun setContent() {
        composeRule.setContent {
            CameraTheme {
                CountDownTimer(
                    value = value,
                    modifier = Modifier.testTag(TAG),
                )
            }
        }
    }

    private companion object {
        private const val TAG = "timer"
    }
}
