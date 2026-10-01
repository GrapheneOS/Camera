package app.grapheneos.camera.ui.components.valuechip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ValueChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun valueChip_showsItsText() {
        composeRule.setContent {
            CameraTheme {
                ValueChip(text = TEXT)
            }
        }

        composeRule.onNodeWithText(TEXT).assertIsDisplayed()
    }

    private companion object {
        private const val TEXT = "3968K"
    }
}
