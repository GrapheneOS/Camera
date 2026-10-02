package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.adjustmentbar.gesture.AdjustmentBarState
import app.grapheneos.camera.ui.components.adjustmentbar.gesture.rememberAdjustmentBarState
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdjustmentBarStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var value by mutableFloatStateOf(0f)
    private var touchSlop = 0f
    private val changes = mutableListOf<Float>()
    private var applyChange: (Float) -> Unit = { newValue -> value = newValue }
    private lateinit var state: AdjustmentBarState

    @Test
    fun state_valueChangedDuringADrag_isFollowedOnRelease() {
        setContent()

        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(center)
            moveBy(Offset(x = -touchSlop - 3.4f * TICK_SPACING_PX, y = 0f))
        }
        value = 0f
        composeRule.onNodeWithTag(TAG).performTouchInput { up() }

        composeRule.runOnIdle {
            assertEquals(listOf(3f), changes)
            assertEquals(0f, value)
            assertEquals(12f, state.position)
        }
    }

    @Test
    fun state_callerIgnoresADrag_isFollowedOnReleaseAndTheNextDragStartsThere() {
        applyChange = {}
        setContent()

        drag(ticks = 4f)
        composeRule.runOnIdle {
            assertEquals(12f, state.position)
            changes.clear()
        }
        drag(ticks = 1f)

        composeRule.runOnIdle { assertEquals(listOf(1f), changes) }
    }

    @Test
    fun state_callerClampsADrag_isFollowedOnReleaseAndTheNextDragStartsThere() {
        applyChange = { newValue -> value = min(newValue, 2f) }
        setContent()

        drag(ticks = 5f)
        composeRule.runOnIdle {
            assertEquals(14f, state.position)
            changes.clear()
        }
        drag(ticks = 1f)

        composeRule.runOnIdle { assertEquals(listOf(3f), changes) }
    }

    @Test
    fun state_callerAppliesTheValueLate_stillGetsTheNearestTickOnRelease() {
        var lateValue = value
        applyChange = { newValue -> lateValue = newValue }
        setContent()

        drag(ticks = 1.7f)
        composeRule.runOnIdle { value = lateValue }

        composeRule.runOnIdle {
            assertEquals(listOf(1f, 2f), changes)
            assertEquals(2f, value)
            assertEquals(14f, state.position)
        }
    }

    private fun drag(ticks: Float) {
        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(center)
            moveBy(Offset(x = -touchSlop, y = 0f))
            repeat(DRAG_MOVES) {
                moveBy(Offset(x = -ticks * TICK_SPACING_PX / DRAG_MOVES, y = 0f))
            }
            up()
        }
    }

    private fun setContent() {
        composeRule.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
            state = rememberAdjustmentBarState(
                value = value,
                scale = SCALE,
                tickSpacing = TICK_SPACING_PX,
                onValueChange = { newValue ->
                    changes += newValue
                    applyChange(newValue)
                },
                onValueChangeFinished = {},
            )
            Box(
                modifier = Modifier
                    .testTag(TAG)
                    .size(200.dp)
                    .draggable(
                        state = state,
                        orientation = Orientation.Horizontal,
                        onDragStopped = { state.release() },
                    ),
            )
        }
    }

    private companion object {
        private const val TAG = "ruler"
        private const val TICK_SPACING_PX = 10f
        private const val DRAG_MOVES = 20
        private val SCALE = AdjustmentBarScale(
            valueRange = -12f..12f,
            steps = 23,
        )
    }
}
