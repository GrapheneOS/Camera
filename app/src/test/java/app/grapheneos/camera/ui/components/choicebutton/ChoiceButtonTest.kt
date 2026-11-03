package app.grapheneos.camera.ui.components.choicebutton

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.grapheneos.camera.ui.core.theme.CameraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChoiceButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selected by mutableStateOf(false)
    private var enabled by mutableStateOf(true)
    private var clicks = 0

    @Test
    fun choiceButton_isARadioButtonThatReadsItsSelection() {
        setContent()

        val button = composeRule.onNodeWithText(LABEL)
        button.assert(
            SemanticsMatcher.expectValue(
                key = SemanticsProperties.Role,
                expectedValue = Role.RadioButton,
            ),
        )
        button.assertIsNotSelected()

        selected = true

        button.assertIsSelected()
    }

    @Test
    fun choiceButton_clicked_callsOnClick() {
        setContent()

        composeRule.onNodeWithText(LABEL).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun choiceButton_disabled_ignoresClicks() {
        enabled = false
        setContent()

        composeRule.onNodeWithText(LABEL)
            .assertIsNotEnabled()
            .performClick()

        assertEquals(0, clicks)
    }

    private fun setContent() {
        composeRule.setContent {
            CameraTheme {
                ChoiceButton(
                    text = LABEL,
                    selected = selected,
                    onClick = { clicks++ },
                    enabled = enabled,
                )
            }
        }
    }

    private companion object {
        const val LABEL = "1×"
    }
}
