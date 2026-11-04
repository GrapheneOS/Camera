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
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysUiState
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

    @Test
    fun lifecycleAndRemovalFromComposition_startAndStopLevelTracking() {
        val owner = ScreenOwner()
        val model = FakeScreenModel()
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
            assertEquals(emptyList<Action>(), model.actions)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.runOnIdle {
            assertEquals(listOf(Action.ScreenResumed), model.actions)
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        composeRule.runOnIdle {
            assertEquals(listOf(Action.ScreenResumed, Action.ScreenPaused), model.actions)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.runOnIdle { visible.value = false }
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    Action.ScreenResumed,
                    Action.ScreenPaused,
                    Action.ScreenResumed,
                    Action.ScreenPaused,
                ),
                model.actions,
            )
        }
    }

    private class FakeScreenModel : PreviewOverlaysScreenModel {
        override val uiState = MutableStateFlow(PreviewOverlaysUiState())
        override val levelUiState = MutableStateFlow(LevelUiState())
        val actions = mutableListOf<Action>()

        override fun onAction(action: Action) {
            actions += action
        }
    }

    private class ScreenOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
