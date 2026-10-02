package app.grapheneos.camera.ui.components.zoom

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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Assert.assertEquals
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
    private var expansions = 0
    private var finishes = 0
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

        swipe(ticks = 3f)

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
    fun zoomControl_expanded_isASliderInsteadOfStops() {
        expanded = true
        setContent()

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertExists()
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

        swipe(ticks = 3f)

        composeRule.runOnIdle {
            assertEquals(0, expansions)
            assertEquals(emptyList<Float>(), changes)
        }
    }

    private fun swipe(ticks: Float) {
        composeRule.onNodeWithTag(TAG).performTouchInput {
            val tickSpacing = TICK_SPACING.toPx()

            down(center)
            moveBy(Offset(x = -touchSlop, y = 0f))
            repeat(DRAG_MOVES) {
                moveBy(Offset(x = -ticks * tickSpacing / DRAG_MOVES, y = 0f))
            }
            up()
        }
    }

    private fun setContent() {
        composeRule.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
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
                )
            }
        }
    }

    private companion object {
        private const val TAG = "zoomControl"
        private const val EXPAND_LABEL = "Adjust zoom"
        private const val DRAG_MOVES = 20
        private val TICK_SPACING = 8.75.dp
    }
}
