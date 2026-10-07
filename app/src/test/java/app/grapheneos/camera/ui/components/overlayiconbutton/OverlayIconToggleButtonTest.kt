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
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.grapheneos.camera.ui.core.CameraTheme
import app.grapheneos.camera.ui.core.PREVIEW_MIC_ICON
import app.grapheneos.camera.ui.core.PREVIEW_MIC_OFF_ICON
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayIconToggleButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var checked by mutableStateOf(false)
    private val checkedChanges = mutableListOf<Boolean>()

    @Test
    fun overlayIconToggleButton_isACheckboxNamedByItsCaller() {
        checked = true
        setContent()

        composeRule.onNodeWithContentDescription(DESCRIPTION)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsOn()
    }

    @Test
    fun overlayIconToggleButton_clicked_reportsTheOppositeState() {
        setContent()

        composeRule.onNodeWithContentDescription(DESCRIPTION).performClick()

        assertEquals(listOf(true), checkedChanges)
    }

    private fun setContent() {
        composeRule.setContent {
            CameraTheme {
                OverlayIconToggleButton(
                    checked = checked,
                    onCheckedChange = { checkedChanges += it },
                    icon = PREVIEW_MIC_ICON,
                    modifier = Modifier.semantics { contentDescription = DESCRIPTION },
                    checkedIcon = PREVIEW_MIC_OFF_ICON,
                )
            }
        }
    }

    private companion object {
        private const val DESCRIPTION = "Mute audio"
    }
}
