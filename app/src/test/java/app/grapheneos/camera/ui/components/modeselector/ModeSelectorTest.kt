package app.grapheneos.camera.ui.components.modeselector

import androidx.compose.foundation.layout.fillMaxWidth
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
import app.grapheneos.camera.ui.components.modeselector.gesture.ModeSelectorState
import app.grapheneos.camera.ui.components.modeselector.gesture.rememberModeSelectorState
import app.grapheneos.camera.ui.core.CameraTheme
import kotlin.math.sign
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModeSelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selectedIndex by mutableIntStateOf(1)
    private var enabled by mutableStateOf(true)
    private var switching by mutableStateOf(false)
    private var layoutDirection = LayoutDirection.Ltr
    private var acceptsSelection = true
    private var touchSlop = 0f
    private val selections = mutableListOf<Int>()
    private val haptics = mutableListOf<HapticFeedbackType>()
    private val hapticFeedback = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            haptics += hapticFeedbackType
        }
    }
    private lateinit var state: ModeSelectorState

    @Test
    fun modeSelector_modesAreTabs_withTheSelectedOneSelected() {
        setContent()

        composeRule.onNodeWithText("Photo")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsSelected()
        composeRule.onNodeWithText("Portrait").assertIsNotSelected()
    }

    @Test
    fun modeSelector_modeTapped_settlesOnItAndReportsIt() {
        setContent()

        composeRule.onNodeWithText("Night Sight").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(2), selections)
            assertEquals(2f, state.position)
        }
    }

    @Test
    fun modeSelector_selectedModeTapped_reportsNothing() {
        setContent()

        composeRule.onNodeWithText("Photo").performClick()

        composeRule.runOnIdle { assertEquals(emptyList<Int>(), selections) }
    }

    @Test
    fun modeSelector_draggedToTheEnd_settlesOnTheLastMode() {
        setContent()

        drag(pixels = -FAR)

        composeRule.runOnIdle {
            assertEquals(listOf(LABELS.lastIndex), selections)
            assertEquals(LABELS.lastIndex.toFloat(), state.position)
        }
    }

    @Test
    fun modeSelector_draggedAcrossModes_ticksWhenEachBecomesTheNearest() {
        setContent()

        drag(pixels = -ACROSS)

        composeRule.runOnIdle {
            assertEquals(List(size = 2) { HapticFeedbackType.SegmentTick }, haptics)
        }
    }

    @Test
    fun modeSelector_farModeTapped_ticksForEachModeTheStripGlidesPast() {
        setContent()

        composeRule.onNodeWithText("Panorama").performClick()

        composeRule.runOnIdle {
            assertEquals(List(size = 2) { HapticFeedbackType.SegmentTick }, haptics)
        }
    }

    @Test
    fun modeSelector_switching_ignoresTapsAndDrags() {
        switching = true
        setContent()

        composeRule.onNodeWithText("Night Sight").performClick()
        drag(pixels = -FAR)

        composeRule.runOnIdle {
            assertEquals(emptyList<Int>(), selections)
            assertEquals(1f, state.position)
        }
    }

    @Test
    fun modeSelector_draggedLessThanHalfAMode_returnsWithoutReporting() {
        setContent()

        drag(pixels = -NUDGE)

        composeRule.runOnIdle {
            assertEquals(emptyList<Int>(), selections)
            assertEquals(1f, state.position)
        }
    }

    @Test
    fun modeSelector_selectionNotTaken_returnsToTheSelectedMode() {
        acceptsSelection = false
        setContent()

        composeRule.onNodeWithText("Night Sight").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(2), selections)
            assertEquals(1f, state.position)
        }
    }

    @Test
    fun modeSelector_selectedIndexChangedByTheCaller_isFollowedSilently() {
        setContent()

        selectedIndex = 3

        composeRule.runOnIdle {
            assertEquals(3f, state.position)
            assertEquals(emptyList<Int>(), selections)
            assertEquals(emptyList<HapticFeedbackType>(), haptics)
        }
    }

    @Test
    fun modeSelector_draggedRightInRtl_settlesOnTheLastMode() {
        layoutDirection = LayoutDirection.Rtl
        setContent()

        drag(pixels = FAR)

        composeRule.runOnIdle {
            assertEquals(listOf(LABELS.lastIndex), selections)
            assertEquals(LABELS.lastIndex.toFloat(), state.position)
        }
    }

    @Test
    fun modeSelector_settleNow_reportsTheModeBeforeTheStripArrives() {
        setContent()

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithText("Night Sight").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle { state.settleNow() }

            composeRule.runOnIdle {
                assertEquals(listOf(2), selections)
                assertEquals(2f, state.position)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun modeSelector_disabled_ignoresTapsAndDrags() {
        enabled = false
        setContent()

        composeRule.onNodeWithText("Night Sight").performClick()
        drag(pixels = -FAR)

        composeRule.onNodeWithText("Night Sight").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(emptyList<Int>(), selections)
            assertEquals(1f, state.position)
        }
    }

    private fun drag(pixels: Float) {
        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(center)
            moveBy(Offset(x = touchSlop * pixels.sign, y = 0f))
            repeat(DRAG_MOVES) {
                moveBy(Offset(x = pixels / DRAG_MOVES, y = 0f))
            }
            up()
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
                    state = rememberModeSelectorState(initialIndex = selectedIndex)
                    ModeSelector(
                        labels = LABELS,
                        selectedIndex = selectedIndex,
                        onModeSelected = { index ->
                            selections += index
                            if (acceptsSelection) {
                                selectedIndex = index
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TAG),
                        enabled = enabled,
                        switching = switching,
                        state = state,
                    )
                }
            }
        }
    }

    private companion object {
        private const val TAG = "modeSelector"
        private const val DRAG_MOVES = 20
        private const val FAR = 5_000f
        private const val NUDGE = 10f
        private const val ACROSS = 600f
        private val LABELS = listOf("Portrait", "Photo", "Night Sight", "Panorama")
    }
}
