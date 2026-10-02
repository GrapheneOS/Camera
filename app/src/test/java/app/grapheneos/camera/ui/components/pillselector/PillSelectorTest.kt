package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PillSelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selectedIndex by mutableIntStateOf(0)
    private var enabled by mutableStateOf(true)
    private val clicks = mutableListOf<Int>()

    @Test
    fun pillSelector_itemsAreRadioButtons() {
        setContent()

        item(label = "B").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        )
    }

    @Test
    fun pillSelector_selectedItem_isTheOnlySelectedOne() {
        selectedIndex = 1
        setContent()

        item(label = "B").assertIsSelected()
        item(label = "A").assertIsNotSelected()
        item(label = "C").assertIsNotSelected()
    }

    @Test
    fun pillSelector_itemTapped_reportsItsIndexWithoutSelectingIt() {
        setContent()

        item(label = "C").performClick()

        assertEquals(listOf(2), clicks)
        item(label = "A").assertIsSelected()
    }

    @Test
    fun pillSelector_disabled_ignoresTaps() {
        enabled = false
        setContent()

        item(label = "B").performClick()

        item(label = "B").assertIsNotEnabled()
        assertEquals(emptyList<Int>(), clicks)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pillSelector_selectedIndexOutOfRange_isRejected() {
        selectedIndex = LABELS.size
        setContent()
    }

    private fun setContent() {
        composeRule.setContent {
            CameraTheme {
                PillSelector(
                    selectedIndex = selectedIndex,
                    itemCount = LABELS.size,
                    onItemClick = { index -> clicks += index },
                    enabled = enabled,
                ) { index ->
                    BasicText(
                        text = LABELS[index],
                        modifier = Modifier.size(48.dp),
                        color = contentColor,
                    )
                }
            }
        }
    }

    private fun item(label: String): SemanticsNodeInteraction {
        return composeRule.onNodeWithText(label)
    }

    private companion object {
        private val LABELS = listOf("A", "B", "C")
    }
}
