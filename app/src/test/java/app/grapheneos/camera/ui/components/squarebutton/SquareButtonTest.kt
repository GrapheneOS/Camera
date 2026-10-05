package app.grapheneos.camera.ui.components.squarebutton

import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.grapheneos.camera.ui.core.CameraTheme
import app.grapheneos.camera.ui.core.PREVIEW_RESTART_ICON
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SquareButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var enabled by mutableStateOf(true)
    private var hasLongClick by mutableStateOf(true)
    private var clicks = 0
    private var longClicks = 0

    @Test
    fun squareButton_isAButtonNamedByItsCaller() {
        setContent()

        composeRule.onNodeWithContentDescription(DESCRIPTION).assert(
            SemanticsMatcher.expectValue(
                key = SemanticsProperties.Role,
                expectedValue = Role.Button,
            ),
        )
    }

    @Test
    fun squareButton_clicked_callsOnClick() {
        setContent()

        composeRule.onNodeWithContentDescription(DESCRIPTION).performClick()

        assertEquals(1, clicks)
        assertEquals(0, longClicks)
    }

    @Test
    fun squareButton_longClicked_callsOnLongClickOnly() {
        setContent()

        composeRule.onNodeWithContentDescription(DESCRIPTION).performTouchInput { longClick() }

        assertEquals(1, longClicks)
        assertEquals(0, clicks)
    }

    @Test
    fun squareButton_announcesItsLongClickLabel() {
        setContent()

        val action = composeRule.onNodeWithContentDescription(DESCRIPTION)
            .fetchSemanticsNode()
            .config[SemanticsActions.OnLongClick]

        assertEquals(LONG_CLICK_LABEL, action.label)
    }

    @Test
    fun squareButton_withoutOnLongClick_hasNoLongClickAction() {
        hasLongClick = false
        setContent()

        composeRule.onNodeWithContentDescription(DESCRIPTION)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }

    @Test
    fun squareButton_disabled_ignoresClicksAndLongClicks() {
        enabled = false
        setContent()

        val button = composeRule.onNodeWithContentDescription(DESCRIPTION)
        button.assertIsNotEnabled()
        button.performClick()
        button.performTouchInput { longClick() }

        assertEquals(0, clicks)
        assertEquals(0, longClicks)
    }

    private fun setContent() {
        composeRule.setContent {
            CameraTheme {
                SquareButton(
                    onClick = { clicks += 1 },
                    modifier = Modifier.semantics { contentDescription = DESCRIPTION },
                    enabled = enabled,
                    onLongClick = { longClicks += 1 }.takeIf { hasLongClick },
                    onLongClickLabel = LONG_CLICK_LABEL.takeIf { hasLongClick },
                ) {
                    Icon(
                        imageVector = PREVIEW_RESTART_ICON,
                        contentDescription = null,
                    )
                }
            }
        }
    }

    private companion object {
        private const val DESCRIPTION = "Switch camera"
        private const val LONG_CLICK_LABEL = "Share"
    }
}
