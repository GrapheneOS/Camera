package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
    private val model = mockk<PreviewOverlaysScreenModel> {
        every { uiState } returns MutableStateFlow(PreviewOverlaysUiState())
        every { levelUiState } returns MutableStateFlow(LevelUiState())
        every { onAction(capture(actions)) } just runs
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
                            previewBounds = DpRect(0.dp, 0.dp, 240.dp, 320.dp),
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
