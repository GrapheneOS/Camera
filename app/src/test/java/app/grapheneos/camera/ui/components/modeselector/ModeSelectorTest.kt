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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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
    private var labels by mutableStateOf(LABELS)
    private var shown by mutableStateOf(true)
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
    fun modeSelector_eachTabCarriesItsOwnLabel() {
        setContent()

        composeRule.onNodeWithText("Photo", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    }

    @Test
    fun modeSelector_isOneRowOfAllModes() {
        setContent()

        composeRule.onNodeWithTag(TAG).assert(
            SemanticsMatcher("is one row of ${LABELS.size} modes") { node ->
                val info = node.config.getOrNull(SemanticsProperties.CollectionInfo)

                info?.rowCount == 1 && info.columnCount == LABELS.size
            },
        )
    }

    @Test
    fun modeSelector_otherModes_canBeSelectedThroughAccessibilityActions() {
        setContent()

        val actions = composeRule.onNodeWithText("Photo")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]

        composeRule.runOnIdle { actions.single { action -> action.label == "Panorama" }.action() }

        composeRule.runOnIdle {
            assertEquals(listOf(3), selections)
            assertEquals(3f, state.position)
        }
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
    fun modeSelector_flickedOntoTheModeItIsGlidingTo_settlesOnItAndReportsIt() {
        setContent()

        withManualClock {
            composeRule.onNodeWithText("Night Sight").performClick()
            composeRule.mainClock.advanceTimeUntil { state.position > MID_GLIDE }
            composeRule.onNodeWithTag(TAG).performTouchInput {
                down(center)
                moveBy(
                    delta = Offset(x = -touchSlop - NUDGE, y = 0f),
                    delayMillis = 0,
                )
                up()
            }
        }

        composeRule.runOnIdle {
            assertEquals(listOf(2), selections)
            assertEquals(2f, state.position)
        }
    }

    @Test
    fun modeSelector_secondFingerTapsTheModeTheDragEndsOn_settlesOnItAndReportsIt() {
        setContent()
        val nightSight = centerOf(label = "Night Sight")
        val distance = nightSight.x - centerOf(label = "Photo").x

        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(pointerId = 0, position = center)
            moveBy(pointerId = 0, delta = Offset(x = -touchSlop - NUDGE, y = 0f))
            down(pointerId = 1, position = nightSight - Offset(x = NUDGE, y = 0f))
            up(pointerId = 1)
            moveBy(pointerId = 0, delta = Offset(x = NUDGE - distance, y = 0f))
            up(pointerId = 0)
        }

        composeRule.runOnIdle {
            assertEquals(listOf(2), selections)
            assertEquals(2f, state.position)
        }
    }

    @Test
    fun modeSelector_switchingStartsMidDrag_returnsWithoutReporting() {
        setContent()

        dragWithoutLifting(pixels = -ACROSS)
        switching = true
        composeRule.onNodeWithTag(TAG).performTouchInput { up() }

        composeRule.runOnIdle {
            assertEquals(emptyList<Int>(), selections)
            assertEquals(1f, state.position)
        }
    }

    @Test
    fun modeSelector_dragCanceled_returnsWithoutReporting() {
        setContent()

        dragWithoutLifting(pixels = -ACROSS)
        composeRule.onNodeWithTag(TAG).performTouchInput { cancel() }

        composeRule.runOnIdle {
            assertEquals(emptyList<Int>(), selections)
            assertEquals(1f, state.position)
        }
    }

    @Test
    fun modeSelector_selectedIndexChangedAfterADrag_isFollowed() {
        setContent()

        drag(pixels = -FAR)
        composeRule.waitForIdle()
        selectedIndex = 0

        composeRule.runOnIdle { assertEquals(0f, state.position) }
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
    fun modeSelector_selectionNotTakenAndSettledBeforeTheNextFrame_returnsToTheSelectedMode() {
        acceptsSelection = false
        setContent()

        withManualClock {
            composeRule.onNodeWithText("Night Sight").performClick()
            composeRule.runOnIdle { state.settleNow() }
        }

        composeRule.runOnIdle {
            assertEquals(listOf(2), selections)
            assertEquals(1f, state.position)
        }
    }

    @Test
    fun modeSelector_modesRemovedMidSettle_dropsTheSettle() {
        setContent()

        withManualClock {
            composeRule.onNodeWithText("Panorama").performClick()
            composeRule.mainClock.advanceTimeUntil { state.position > selectedIndex }
            labels = LABELS.take(2)
            recomposeInNextFrame()
        }

        composeRule.runOnIdle {
            assertEquals(emptyList<Int>(), selections)
            assertEquals(1f, state.position)
        }
    }

    @Test
    fun modeSelector_modeInsertedMidSettle_dropsTheSettle() {
        setContent()

        withManualClock {
            composeRule.onNodeWithText("Panorama").performClick()
            composeRule.mainClock.advanceTimeUntil { state.position > selectedIndex }
            labels = listOf("Portrait", "Photo", "Night Sight", "HDR", "Panorama")
            recomposeInNextFrame()
        }

        composeRule.runOnIdle {
            assertEquals(emptyList<Int>(), selections)
            assertEquals(1f, state.position)
        }
    }

    @Test
    fun modeSelector_modesInsertedBeforeTheSelectedOne_jumpsStraightToIt() {
        setContent()

        withManualClock {
            labels = listOf("Auto", "HDR") + LABELS
            selectedIndex = 3
            recomposeInNextFrame()

            assertEquals(3f, state.position)
        }
    }

    @Test
    fun modeSelector_leftCompositionMidSettle_finishesTheSettle() {
        setContent()

        withManualClock {
            composeRule.onNodeWithText("Night Sight").performClick()
            composeRule.mainClock.advanceTimeUntil { state.position > selectedIndex }
            shown = false
            recomposeInNextFrame()

            assertEquals(listOf(2), selections)
            assertEquals(2f, state.position)
        }
    }

    @Test
    fun modeSelector_leftCompositionMidDrag_followsSelectedIndexOnReturn() {
        setContent()

        dragWithoutLifting(pixels = -ACROSS)
        shown = false
        composeRule.waitForIdle()
        shown = true
        selectedIndex = 0

        composeRule.runOnIdle {
            assertEquals(emptyList<Int>(), selections)
            assertEquals(0f, state.position)
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

        withManualClock {
            composeRule.onNodeWithText("Night Sight").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle { state.settleNow() }

            composeRule.runOnIdle { assertEquals(listOf(2), selections) }
        }

        composeRule.runOnIdle {
            assertEquals(listOf(2), selections)
            assertEquals(2f, state.position)
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
            pressAndMove(pixels = pixels)
            up()
        }
    }

    private fun dragWithoutLifting(pixels: Float) {
        composeRule.onNodeWithTag(TAG).performTouchInput { pressAndMove(pixels = pixels) }
    }

    private fun TouchInjectionScope.pressAndMove(pixels: Float) {
        down(center)
        moveBy(Offset(x = touchSlop * pixels.sign, y = 0f))
        repeat(DRAG_MOVES) {
            moveBy(Offset(x = pixels / DRAG_MOVES, y = 0f))
        }
    }

    private fun centerOf(label: String): Offset {
        val strip = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().boundsInRoot

        return composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center -
            strip.topLeft
    }

    private fun recomposeInNextFrame() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun withManualClock(block: () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        try {
            block()
        } finally {
            composeRule.mainClock.autoAdvance = true
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
                    if (shown) {
                        ModeSelector(
                            labels = labels,
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
    }

    private companion object {
        private const val TAG = "modeSelector"
        private const val DRAG_MOVES = 20
        private const val FAR = 5_000f
        private const val NUDGE = 10f
        private const val ACROSS = 600f
        private const val MID_GLIDE = 1.6f
        private val LABELS = listOf("Portrait", "Photo", "Night Sight", "Panorama")
    }
}
