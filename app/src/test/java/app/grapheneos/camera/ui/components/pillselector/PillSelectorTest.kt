package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.pillselector.model.PillSelectorSpacing
import app.grapheneos.camera.ui.core.CameraTheme
import kotlin.math.sign
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
    private var draggable = false
    private var layoutDirection = LayoutDirection.Ltr
    private var touchSlop = 0f
    private val selections = mutableListOf<Int>()
    private val haptics = mutableListOf<HapticFeedbackType>()
    private val hapticFeedback = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            haptics += hapticFeedbackType
        }
    }

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

        assertEquals(listOf(2), selections)
        item(label = "A").assertIsSelected()
    }

    @Test
    fun pillSelector_selectedIndexChanged_movesTheSelection() {
        setContent()

        selectedIndex = 2

        item(label = "C").assertIsSelected()
        item(label = "A").assertIsNotSelected()
    }

    @Test
    fun pillSelector_draggedOntoTheNextItem_reportsItWithATick() {
        draggable = true
        setContent()

        drag(items = 1f)

        composeRule.runOnIdle {
            assertEquals(listOf(1), selections)
            assertEquals(listOf(HapticFeedbackType.SegmentTick), haptics)
        }
    }

    @Test
    fun pillSelector_draggedLessThanHalfAnItem_reportsTheSelectedOne() {
        draggable = true
        setContent()

        drag(items = 0.3f)

        composeRule.runOnIdle {
            assertEquals(listOf(0), selections)
            assertEquals(emptyList<HapticFeedbackType>(), haptics)
        }
    }

    @Test
    fun pillSelector_draggedLeftInRtl_reportsTheNextItem() {
        draggable = true
        layoutDirection = LayoutDirection.Rtl
        setContent()

        drag(items = -1f)

        composeRule.runOnIdle { assertEquals(listOf(1), selections) }
    }

    @Test
    fun pillSelector_dragCanceled_reportsNothing() {
        draggable = true
        setContent()

        composeRule.onNodeWithTag(TAG).performTouchInput {
            pressAndMove(items = 1f)
            cancel()
        }

        composeRule.runOnIdle { assertEquals(emptyList<Int>(), selections) }
    }

    @Test
    fun pillSelector_disabledMidDrag_reportsNothing() {
        draggable = true
        setContent()

        composeRule.onNodeWithTag(TAG).performTouchInput { pressAndMove(items = 1f) }
        enabled = false
        composeRule.onNodeWithTag(TAG).performTouchInput { up() }

        composeRule.runOnIdle { assertEquals(emptyList<Int>(), selections) }
    }

    @Test
    fun pillSelector_notDraggable_ignoresDrags() {
        setContent()

        drag(items = 1f)

        composeRule.runOnIdle {
            assertEquals(emptyList<Int>(), selections)
            assertEquals(emptyList<HapticFeedbackType>(), haptics)
        }
    }

    @Test
    fun pillSelector_disabled_ignoresTaps() {
        enabled = false
        setContent()

        item(label = "B").performClick()

        item(label = "B").assertIsNotEnabled()
        assertEquals(emptyList<Int>(), selections)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pillSelector_selectedIndexOutOfRange_isRejected() {
        selectedIndex = LABELS.size
        setContent()
    }

    private fun drag(items: Float) {
        composeRule.onNodeWithTag(TAG).performTouchInput {
            pressAndMove(items = items)
            up()
        }
    }

    private fun TouchInjectionScope.pressAndMove(items: Float) {
        down(center)
        moveBy(Offset(x = touchSlop * items.sign, y = 0f))
        repeat(DRAG_MOVES) {
            moveBy(Offset(x = items * ITEM_SIZE.toPx() / DRAG_MOVES, y = 0f))
        }
    }

    private fun setContent() {
        composeRule.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
            CameraTheme {
                CompositionLocalProvider(
                    LocalHapticFeedback provides hapticFeedback,
                    LocalLayoutDirection provides layoutDirection,
                ) {
                    PillSelector(
                        selectedIndex = selectedIndex,
                        itemCount = LABELS.size,
                        onItemSelected = { index -> selections += index },
                        modifier = Modifier.testTag(TAG),
                        enabled = enabled,
                        draggable = draggable,
                        spacing = PillSelectorSpacing.Joined,
                    ) { index ->
                        BasicText(
                            text = LABELS[index],
                            modifier = Modifier.size(ITEM_SIZE),
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }

    private fun item(label: String): SemanticsNodeInteraction {
        return composeRule.onNodeWithText(label)
    }

    private companion object {
        private const val TAG = "pillSelector"
        private const val DRAG_MOVES = 10
        private val ITEM_SIZE = 48.dp
        private val LABELS = listOf("A", "B", "C")
    }
}
