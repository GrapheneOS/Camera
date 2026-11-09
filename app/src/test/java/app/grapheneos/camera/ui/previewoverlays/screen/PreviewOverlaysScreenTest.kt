package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.grapheneos.camera.ui.core.theme.CameraTheme
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.LifecycleAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewOverlaysUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.ZoomUiModel
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewOverlaysScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<Action>()
    private val uiState = MutableStateFlow(PreviewOverlaysUiState())
    private val model = mockk<PreviewOverlaysScreenModel> {
        every { uiState } returns this@PreviewOverlaysScreenTest.uiState
        every { levelUiState } returns MutableStateFlow(LevelUiState())
        every { onAction(capture(actions)) } just runs
    }

    @Test
    fun changingTheZoomRange_doesNotReleaseTheFingerHoldingTheControl() {
        uiState.value = PreviewOverlaysUiState(
            zoom = ZoomUiModel(
                ratio = 1f,
                range = 0.5f..8f,
                presets = persistentListOf(0.5f, 1f, 2f),
            ),
        )
        composeRule.setContent {
            CameraTheme {
                PreviewOverlaysScreen(
                    previewBounds = DpRect(
                        left = 0.dp,
                        top = 0.dp,
                        right = 240.dp,
                        bottom = 320.dp,
                    ),
                    shutterRowInset = 0.dp,
                    screenModel = model,
                )
            }
        }
        composeRule.onNodeWithContentDescription("1×").performTouchInput { down(center) }
        composeRule.runOnIdle {
            uiState.value = uiState.value.copy(
                zoom = ZoomUiModel(
                    ratio = 1f,
                    range = 1f..4f,
                    presets = persistentListOf(1f, 2f),
                ),
            )
        }
        composeRule.runOnIdle {
            val presses = actions.filterIsInstance<Action.ZoomAction.ControlPressChanged>()
            assertEquals(listOf(Action.ZoomAction.ControlPressChanged(isPressed = true)), presses)
        }
        composeRule.onNodeWithContentDescription("1×").performTouchInput { up() }
        composeRule.runOnIdle {
            assertEquals(Action.ZoomAction.ControlPressChanged(isPressed = false), actions.last())
        }
    }

    @Test
    fun lifecycleAndRemovalFromComposition_startAndStopLevelTracking() {
        val owner = ScreenOwner()
        val visible = mutableStateOf(true)
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeRule.setContent {
            CameraTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    if (visible.value) {
                        PreviewOverlaysScreen(
                            previewBounds = DpRect(
                                left = 0.dp,
                                top = 0.dp,
                                right = 240.dp,
                                bottom = 320.dp,
                            ),
                            shutterRowInset = 0.dp,
                            screenModel = model,
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(emptyList<Action>(), actions)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.runOnIdle {
            assertEquals(listOf(LifecycleAction.ScreenResumed), actions)
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf(LifecycleAction.ScreenResumed, LifecycleAction.ScreenPaused),
                actions,
            )
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.runOnIdle { visible.value = false }
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    LifecycleAction.ScreenResumed,
                    LifecycleAction.ScreenPaused,
                    LifecycleAction.ScreenResumed,
                    LifecycleAction.ScreenPaused,
                ),
                actions,
            )
        }
    }

    private class ScreenOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
