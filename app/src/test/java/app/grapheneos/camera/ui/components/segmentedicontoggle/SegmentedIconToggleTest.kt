package app.grapheneos.camera.ui.components.segmentedicontoggle

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.segmentedicontoggle.model.SegmentedIconToggleOption
import app.grapheneos.camera.ui.core.CameraTheme
import app.grapheneos.camera.ui.core.PREVIEW_PHOTO_CAMERA_ICON
import app.grapheneos.camera.ui.core.PREVIEW_VIDEOCAM_ICON
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SegmentedIconToggleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var touchSlop = 0f
    private val selections = mutableListOf<Int>()

    @Test
    fun segmentedIconToggle_optionsAreRadioButtonsNamedByTheirDescription() {
        setContent()

        option(description = PHOTO)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        option(description = VIDEO).assertIsNotSelected()
    }

    @Test
    fun segmentedIconToggle_draggedOntoTheOtherOption_reportsIt() {
        setContent()

        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(center)
            moveBy(Offset(x = touchSlop, y = 0f))
            repeat(DRAG_MOVES) {
                moveBy(Offset(x = OPTION_SIZE.toPx() / DRAG_MOVES, y = 0f))
            }
            up()
        }

        composeRule.runOnIdle { assertEquals(listOf(1), selections) }
    }

    private fun setContent() {
        composeRule.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
            CameraTheme {
                SegmentedIconToggle(
                    options = listOf(
                        SegmentedIconToggleOption(
                            icon = PREVIEW_PHOTO_CAMERA_ICON,
                            contentDescription = PHOTO,
                        ),
                        SegmentedIconToggleOption(
                            icon = PREVIEW_VIDEOCAM_ICON,
                            contentDescription = VIDEO,
                        ),
                    ),
                    selectedIndex = 0,
                    onOptionSelected = { index -> selections += index },
                    modifier = Modifier.testTag(TAG),
                )
            }
        }
    }

    private fun option(description: String): SemanticsNodeInteraction {
        return composeRule.onNodeWithContentDescription(description)
    }

    private companion object {
        private const val PHOTO = "Photo"
        private const val VIDEO = "Video"
        private const val TAG = "segmentedIconToggle"
        private const val DRAG_MOVES = 10
        private val OPTION_SIZE = 48.dp
    }
}
