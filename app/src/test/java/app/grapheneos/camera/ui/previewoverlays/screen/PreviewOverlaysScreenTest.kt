package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.LifecycleAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.ZoomAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewControlUiModel
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewOverlaysUiState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
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

    private val previewBounds = DpRect(
        left = 0.dp,
        top = 0.dp,
        right = 240.dp,
        bottom = 320.dp,
    )
    private val uiState = MutableStateFlow(PreviewOverlaysUiState())
    private val model = mockk<PreviewOverlaysScreenModel>(relaxUnitFun = true) {
        every { uiState } returns this@PreviewOverlaysScreenTest.uiState
        every { levelUiState } returns MutableStateFlow(LevelUiState())
        every { zoomRatio } returns MutableStateFlow(1f)
    }

    @Test
    fun exposureAndZoom_useTheSameSlotAndNeverOverlap() {
        val zoom = PreviewControlUiModel.Zoom(
            range = 0.5f..8f,
            presets = persistentListOf(0.5f, 1f, 2f),
            isExpanded = true,
        )
        uiState.value = PreviewOverlaysUiState(control = zoom)
        setContent()
        val ruler = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        )
        val zoomBottom = ruler.fetchSemanticsNode().boundsInRoot.bottom

        composeRule.runOnIdle {
            uiState.value = uiState.value.copy(
                control = PreviewControlUiModel.Exposure(
                    value = 0f,
                    range = -2f..2f,
                    steps = 23,
                    majorTickInterval = 6,
                ),
            )
        }

        composeRule.onNodeWithContentDescription("Exposure compensation").assertExists()
        assertEquals(zoomBottom, ruler.fetchSemanticsNode().boundsInRoot.bottom)

        composeRule.runOnIdle { uiState.value = uiState.value.copy(control = zoom) }

        composeRule.onNodeWithContentDescription("Exposure compensation").assertDoesNotExist()
        assertEquals(zoomBottom, ruler.fetchSemanticsNode().boundsInRoot.bottom)
    }

    @Test
    fun changingTheZoomRange_doesNotReleaseTheFingerHoldingTheControl() {
        uiState.value = PreviewOverlaysUiState(
            control = PreviewControlUiModel.Zoom(
                range = 0.5f..8f,
                presets = persistentListOf(0.5f, 1f, 2f),
            ),
        )
        setContent()
        val pressed = ZoomAction.ControlPressChanged(isPressed = true)
        val released = ZoomAction.ControlPressChanged(isPressed = false)
        val preset = composeRule.onNodeWithContentDescription("1×")
        preset.performTouchInput { down(center) }
        composeRule.runOnIdle {
            uiState.value = uiState.value.copy(
                control = PreviewControlUiModel.Zoom(
                    range = 1f..4f,
                    presets = persistentListOf(1f, 2f),
                ),
            )
        }
        composeRule.runOnIdle {
            verify(exactly = 1) { model.onAction(pressed) }
            verify(exactly = 0) { model.onAction(released) }
        }
        preset.performTouchInput { up() }
        composeRule.runOnIdle {
            verify(exactly = 1) { model.onAction(released) }
        }
    }

    @Test
    fun lifecycleAndRemovalFromComposition_reportResumeAndPause() {
        val owner = ScreenOwner()
        val visible = mutableStateOf(true)
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeRule.setContent {
            CameraTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    if (visible.value) {
                        PreviewOverlaysScreen(
                            previewBounds = previewBounds,
                            shutterRowInset = 0.dp,
                            screenModel = model,
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle {
            verify(exactly = 0) { model.onAction(any()) }
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()

        verifyOrder {
            model.onAction(LifecycleAction.ScreenResumed)
            model.onAction(LifecycleAction.ScreenPaused)
            model.onAction(LifecycleAction.ScreenResumed)
            model.onAction(LifecycleAction.ScreenPaused)
        }
        verify(exactly = 4) { model.onAction(any()) }
    }

    private fun setContent() {
        composeRule.setContent {
            CameraTheme {
                PreviewOverlaysScreen(
                    previewBounds = previewBounds,
                    shutterRowInset = 0.dp,
                    screenModel = model,
                )
            }
        }
    }

    private class ScreenOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
