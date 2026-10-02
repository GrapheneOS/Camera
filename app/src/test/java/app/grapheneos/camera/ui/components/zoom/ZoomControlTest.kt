package app.grapheneos.camera.ui.components.zoom

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZoomControlTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var value by mutableFloatStateOf(1f)
    private var expanded by mutableStateOf(false)
    private var enabled by mutableStateOf(true)
    private var touchSlop = 0f
    private var longPressTimeoutMillis = 0L
    private var isPressed = false
    private var expansions = 0
    private var finishes = 0
    private val interactionSource = MutableInteractionSource()
    private val changes = mutableListOf<Float>()
    private val stopClicks = mutableListOf<Float>()

    @Test
    fun zoomControl_collapsedStopTapped_reportsTheStop() {
        setContent()

        composeRule.onNodeWithContentDescription("2×").performClick()

        assertEquals(listOf(2f), stopClicks)
        assertEquals(0, expansions)
    }

    @Test
    fun zoomControl_swipedOnTheStops_expandsAndKeepsZoomingWithTheSameTouch() {
        setContent()

        swipe()

        composeRule.runOnIdle {
            assertEquals(1, expansions)
            assertTrue(expanded)
            assertTrue(changes.isNotEmpty())
            assertTrue(changes.zipWithNext().all { (previous, next) -> next > previous })
            assertEquals(1, finishes)
            assertEquals(emptyList<Float>(), stopClicks)
        }
    }

    @Test
    fun zoomControl_longPressedOnTheStops_expandsWithoutSelectingAStop() {
        setContent()

        longPress()
        composeRule.onNodeWithTag(TAG).performTouchInput { up() }

        composeRule.runOnIdle {
            assertEquals(1, expansions)
            assertTrue(expanded)
            assertEquals(emptyList<Float>(), stopClicks)
        }
    }

    @Test
    fun zoomControl_draggedAfterALongPress_keepsZoomingWithTheSameTouch() {
        setContent()

        longPress()
        composeRule.onNodeWithTag(TAG).performTouchInput {
            moveBy(Offset(x = -touchSlop, y = 0f))
            repeat(DRAG_MOVES) {
                moveBy(Offset(x = -SWIPE_TICKS * TICK_SPACING.toPx() / DRAG_MOVES, y = 0f))
            }
            up()
        }

        composeRule.runOnIdle {
            assertEquals(1, expansions)
            assertTrue(changes.isNotEmpty())
            assertEquals(1, finishes)
        }
    }

    @Test
    fun zoomControl_heldAfterExpanding_staysPressedUntilReleased() {
        setContent()

        longPress()
        composeRule.runOnIdle { assertTrue(isPressed) }
        composeRule.onNodeWithTag(TAG).performTouchInput { up() }

        composeRule.runOnIdle { assertFalse(isPressed) }
    }

    @Test
    fun zoomControl_tapped_endsItsPress() {
        setContent()

        composeRule.onNodeWithContentDescription("2×").performClick()

        composeRule.runOnIdle { assertFalse(isPressed) }
    }

    @Test
    fun zoomControl_expanded_isASliderInsteadOfStops() {
        expanded = true
        setContent()

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1×"))
        composeRule.onNodeWithContentDescription("2×").assertDoesNotExist()
    }

    @Test
    fun zoomControl_expandAction_expandsTheStops() {
        setContent()

        composeRule.onNodeWithContentDescription("1×")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .single { action -> action.label == EXPAND_LABEL }
            .action()

        composeRule.runOnIdle { assertEquals(1, expansions) }
    }

    @Test
    fun zoomControl_disabled_ignoresSwipes() {
        enabled = false
        setContent()

        swipe()

        composeRule.runOnIdle {
            assertEquals(0, expansions)
            assertEquals(emptyList<Float>(), changes)
        }
    }

    private fun longPress() {
        composeRule.onNodeWithTag(TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(longPressTimeoutMillis + AFTER_LONG_PRESS_MILLIS)
    }

    private fun swipe() {
        composeRule.onNodeWithTag(TAG).performTouchInput {
            val tickSpacing = TICK_SPACING.toPx()

            down(center)
            moveBy(Offset(x = -touchSlop, y = 0f))
            repeat(DRAG_MOVES) {
                moveBy(Offset(x = -SWIPE_TICKS * tickSpacing / DRAG_MOVES, y = 0f))
            }
            up()
        }
    }

    private fun setContent() {
        composeRule.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
            longPressTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
            isPressed = interactionSource.collectIsPressedAsState().value
            CameraTheme {
                ZoomControl(
                    value = value,
                    onValueChange = { newValue ->
                        changes += newValue
                        value = newValue
                    },
                    valueRange = 0.5f..8f,
                    stops = listOf(0.5f, 1f, 2f),
                    valueSuffix = "×",
                    expanded = expanded,
                    onExpand = {
                        expansions += 1
                        expanded = true
                    },
                    onStopClick = { stop -> stopClicks += stop },
                    expandLabel = EXPAND_LABEL,
                    modifier = Modifier.testTag(TAG),
                    enabled = enabled,
                    onValueChangeFinished = { finishes += 1 },
                    interactionSource = interactionSource,
                )
            }
        }
    }

    private companion object {
        private const val TAG = "zoomControl"
        private const val EXPAND_LABEL = "Adjust zoom"
        private const val DRAG_MOVES = 20
        private const val SWIPE_TICKS = 3f
        private const val AFTER_LONG_PRESS_MILLIS = 100L
        private val TICK_SPACING = 8.75.dp
    }
}
