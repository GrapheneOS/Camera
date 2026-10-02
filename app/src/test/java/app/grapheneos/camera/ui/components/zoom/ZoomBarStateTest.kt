package app.grapheneos.camera.ui.components.zoom

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
import app.grapheneos.camera.ui.components.ruler.rememberRulerBindings
import app.grapheneos.camera.ui.components.zoom.gesture.ZoomBarState
import app.grapheneos.camera.ui.components.zoom.gesture.rememberZoomBarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZoomBarStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var value by mutableFloatStateOf(1f)
    private var touchSlop = 0f
    private val changes = mutableListOf<Float>()
    private var applyChange: (Float) -> Unit = { newValue -> value = newValue }
    private lateinit var state: ZoomBarState

    @Test
    fun state_valueChangedDuringADrag_isFollowedOnRelease() {
        setContent()

        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(center)
            moveBy(Offset(x = -touchSlop - 3.4f * TICK_SPACING_PX, y = 0f))
        }
        value = 1f
        composeRule.onNodeWithTag(TAG).performTouchInput { up() }

        composeRule.runOnIdle {
            assertTrue(changes.isNotEmpty())
            assertEquals(1f, value)
            assertEquals(SCALE.position(value = 1f), state.position)
        }
    }

    @Test
    fun state_callerIgnoresADrag_isFollowedOnRelease() {
        applyChange = {}
        setContent()

        drag(ticks = 2.4f)

        composeRule.runOnIdle {
            assertTrue(changes.isNotEmpty())
            assertEquals(SCALE.position(value = 1f), state.position)
        }
    }

    @Test
    fun state_pulledOffAStopLessThanATick_staysOnIt() {
        setContent()

        drag(ticks = 0.6f)

        composeRule.runOnIdle {
            assertEquals(emptyList<Float>(), changes)
            assertEquals(SCALE.position(value = 1f), state.position)
        }
    }

    @Test
    fun state_pulledOffAStopPastATick_movesOnWithoutAJump() {
        setContent()

        drag(ticks = 1.5f)

        composeRule.runOnIdle {
            assertEquals(SCALE.position(value = 1f) + 0.5f, state.position)
        }
    }

    @Test
    fun state_draggedOntoAStop_holdsThere() {
        value = SCALE.value(position = 3f)
        setContent()

        drag(ticks = 2.6f)

        composeRule.runOnIdle {
            assertEquals(listOf(1f), changes)
            assertEquals(SCALE.position(value = 1f), state.position)
        }
    }

    private fun drag(ticks: Float) {
        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(center)
            moveBy(Offset(x = -touchSlop - ticks * TICK_SPACING_PX, y = 0f))
            up()
        }
    }

    private fun setContent() {
        composeRule.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
            state = rememberZoomBarState(
                value = value,
                scale = SCALE,
                bindings = rememberRulerBindings(
                    value = value,
                    onValueChange = { newValue ->
                        changes += newValue
                        applyChange(newValue)
                    },
                    onValueChangeFinished = {},
                    tickSpacing = TICK_SPACING_PX,
                ),
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
        private val SCALE = ZoomBarScale(
            valueRange = 0.5f..8f,
            stops = listOf(0.5f, 1f, 2f, 8f),
        )
    }
}
