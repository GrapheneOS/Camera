package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraTheme
import app.grapheneos.camera.ui.core.PREVIEW_BRIGHTNESS_HIGH_ICON
import app.grapheneos.camera.ui.core.PREVIEW_BRIGHTNESS_LOW_ICON
import kotlin.math.sign
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdjustmentBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var value by mutableFloatStateOf(0f)
    private var enabled by mutableStateOf(true)
    private var layoutDirection by mutableStateOf(LayoutDirection.Ltr)
    private var touchSlop = 0f
    private var longPressTimeoutMillis = 0L
    private val changes = mutableListOf<Float>()
    private var finishes = 0

    @Test
    fun adjustmentBar_reportsItsRange() {
        setContent()

        bar().assertRangeInfoEquals(
            ProgressBarRangeInfo(
                current = 0f,
                range = -12f..12f,
                steps = 23,
            ),
        )
    }

    @Test
    fun adjustmentBar_draggedLeft_reportsEveryHigherTickThatPassesTheIndicator() {
        setContent()

        drag(ticks = -3.4f)

        assertEquals(listOf(1f, 2f, 3f), changes)
        assertEquals(1, finishes)
    }

    @Test
    fun adjustmentBar_releasedPastHalfATick_settlesOnTheNextOne() {
        setContent()

        drag(ticks = -2.7f)

        assertEquals(listOf(1f, 2f, 3f), changes)
        assertEquals(1, finishes)
    }

    @Test
    fun adjustmentBar_canceledPastHalfATick_staysOnTheLastReportedTick() {
        setContent()

        bar().performTouchInput {
            pressAndMove(ticks = -2.7f)
            cancel()
        }

        composeRule.runOnIdle {
            assertEquals(listOf(1f, 2f), changes)
            assertEquals(1, finishes)
        }
    }

    @Test
    fun adjustmentBar_turnedBackBeforeTheNextTick_reportsNothingNew() {
        setContent()

        bar().performTouchInput {
            down(center)
            moveBy(Offset(x = -touchSlop - 0.4f * tickSpacing(), y = 0f))
            moveBy(Offset(x = 0.3f * tickSpacing(), y = 0f))
            up()
        }

        assertEquals(emptyList<Float>(), changes)
    }

    @Test
    fun adjustmentBar_draggedPastTheEnd_stopsAtTheLastValue() {
        value = 10f
        setContent()

        drag(ticks = -6f)

        assertEquals(listOf(11f, 12f), changes)
    }

    @Test
    fun adjustmentBar_disabled_ignoresDrags() {
        enabled = false
        setContent()

        drag(ticks = -3f)

        bar().assertIsNotEnabled()
        assertEquals(emptyList<Float>(), changes)
    }

    @Test
    fun adjustmentBar_setProgress_snapsToAStep() {
        setContent()

        bar().performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(4.3f)
        }

        assertEquals(listOf(4f), changes)
        assertEquals(1, finishes)
    }

    @Test
    fun adjustmentBar_arrowKeys_stepTheValue() {
        setContent()

        bar().requestFocus()
        bar().performKeyInput { pressKey(Key.DirectionRight) }

        assertEquals(listOf(1f), changes)
        assertEquals(1, finishes)
    }

    @Test
    fun adjustmentBar_arrowKeysInRtl_followTheReadingDirection() {
        layoutDirection = LayoutDirection.Rtl
        setContent()

        bar().requestFocus()
        bar().performKeyInput { pressKey(Key.DirectionRight) }

        assertEquals(listOf(-1f), changes)
    }

    @Test
    fun adjustmentBar_startIconTapped_stepsTowardTheStart() {
        setContent()

        tapIcon(atLeft = true)

        assertEquals(listOf(-1f), changes)
        assertEquals(1, finishes)
    }

    @Test
    fun adjustmentBar_endIconTapped_stepsTowardTheEnd() {
        setContent()

        tapIcon(atLeft = false)

        assertEquals(listOf(1f), changes)
    }

    @Test
    fun adjustmentBar_startIconTappedInRtl_stillStepsTowardTheStart() {
        layoutDirection = LayoutDirection.Rtl
        setContent()

        tapIcon(atLeft = false)

        assertEquals(listOf(-1f), changes)
    }

    @Test
    fun adjustmentBar_endIconTappedAtTheEnd_staysThere() {
        value = 12f
        setContent()

        tapIcon(atLeft = false)

        assertEquals(emptyList<Float>(), changes)
        assertEquals(0, finishes)
    }

    @Test
    fun adjustmentBar_endIconHeld_keepsSteppingUntilReleased() {
        setContent()

        holdIcon(millis = longPressTimeoutMillis + TWO_REPEATS_MILLIS)

        assertEquals(listOf(1f, 2f, 3f), changes)
    }

    @Test
    fun adjustmentBar_endIconHeldAtTheEnd_stopsThere() {
        value = 10f
        setContent()

        holdIcon(millis = longPressTimeoutMillis + TWO_REPEATS_MILLIS)

        assertEquals(listOf(11f, 12f), changes)
    }

    @Test
    fun adjustmentBar_disabled_ignoresIconTaps() {
        enabled = false
        setContent()

        tapIcon(atLeft = true)

        assertEquals(emptyList<Float>(), changes)
    }

    @Test
    fun adjustmentBar_disabledWhileAnIconIsHeld_stopsStepping() {
        setContent()

        composeRule.mainClock.autoAdvance = false
        try {
            bar().performTouchInput { down(Offset(x = width - height / 2f, y = centerY)) }
            composeRule.mainClock.advanceTimeBy(longPressTimeoutMillis + BEFORE_REPEAT_MILLIS)
            enabled = false
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(TWO_REPEATS_MILLIS)
            bar().performTouchInput { up() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        assertEquals(listOf(1f), changes)
    }

    private fun setContent() {
        composeRule.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
            longPressTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                CameraTheme {
                    AdjustmentBar(
                        value = value,
                        onValueChange = { newValue ->
                            changes += newValue
                            value = newValue
                        },
                        valueRange = -12f..12f,
                        steps = 23,
                        modifier = Modifier.testTag(TAG),
                        enabled = enabled,
                        startIcon = PREVIEW_BRIGHTNESS_LOW_ICON,
                        endIcon = PREVIEW_BRIGHTNESS_HIGH_ICON,
                        onValueChangeFinished = { finishes += 1 },
                    )
                }
            }
        }
    }

    private fun tapIcon(atLeft: Boolean) {
        bar().performTouchInput {
            val x = when {
                atLeft -> height / 2f
                else -> width - height / 2f
            }

            click(Offset(x = x, y = centerY))
        }
    }

    private fun holdIcon(millis: Long) {
        composeRule.mainClock.autoAdvance = false
        try {
            bar().performTouchInput { down(Offset(x = width - height / 2f, y = centerY)) }
            composeRule.mainClock.advanceTimeBy(millis)
            bar().performTouchInput { up() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun drag(ticks: Float) {
        bar().performTouchInput {
            pressAndMove(ticks = ticks)
            up()
        }
    }

    private fun TouchInjectionScope.pressAndMove(ticks: Float) {
        down(center)
        moveBy(Offset(x = touchSlop * ticks.sign, y = 0f))
        repeat(DRAG_MOVES) {
            moveBy(Offset(x = ticks * tickSpacing() / DRAG_MOVES, y = 0f))
        }
    }

    private fun tickSpacing(): Float {
        return with(composeRule.density) { TICK_SPACING.toPx() }
    }

    private fun bar(): SemanticsNodeInteraction {
        return composeRule.onNodeWithTag(TAG)
    }

    private companion object {
        private const val TAG = "adjustmentBar"
        private const val DRAG_MOVES = 20
        private const val TWO_REPEATS_MILLIS = 250L
        private const val BEFORE_REPEAT_MILLIS = 50L
        private val TICK_SPACING = 8.75.dp
    }
}
