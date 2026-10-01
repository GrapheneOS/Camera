package app.grapheneos.camera.ui.components.overlayiconbutton

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.grapheneos.camera.ui.core.CameraTheme
import app.grapheneos.camera.ui.core.PREVIEW_RESTART_ICON
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayIconButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var enabled by mutableStateOf(true)
    private var clicks = 0

    @Test
    fun overlayIconButton_isAButtonNamedByItsCaller() {
        setContent()

        composeRule.onNodeWithContentDescription(DESCRIPTION).assert(
            SemanticsMatcher.expectValue(
                key = SemanticsProperties.Role,
                expectedValue = Role.Button,
            ),
        )
    }

    @Test
    fun overlayIconButton_clicked_callsOnClick() {
        setContent()

        composeRule.onNodeWithContentDescription(DESCRIPTION).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun overlayIconButton_disabled_ignoresClicks() {
        enabled = false
        setContent()

        composeRule.onNodeWithContentDescription(DESCRIPTION)
            .assertIsNotEnabled()
            .performClick()

        assertEquals(0, clicks)
    }

    private fun setContent() {
        composeRule.setContent {
            CameraTheme {
                OverlayIconButton(
                    onClick = { clicks += 1 },
                    icon = PREVIEW_RESTART_ICON,
                    modifier = Modifier.semantics { contentDescription = DESCRIPTION },
                    enabled = enabled,
                )
            }
        }
    }

    private companion object {
        private const val DESCRIPTION = "Reset"
    }
}
