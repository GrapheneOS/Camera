package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import app.grapheneos.camera.ui.core.theme.CameraTheme
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.ZoomAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewControlUiModel
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewZoomTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun currentValue_isShownAfterThePresetTransitionEnds() {
        var zoom by mutableStateOf(
            PreviewControlUiModel.Zoom(
                range = 0.5f..8f,
                presets = persistentListOf(0.5f, 1f, 2f),
                isCurrentValueVisible = false,
            ),
        )
        composeRule.setContent {
            CameraTheme {
                PreviewZoom(
                    zoom = zoom,
                    zoomRatio = { 1.8f },
                    onAction = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("1×").assertIsSelected()
        composeRule.onNodeWithContentDescription("1.8×").assertDoesNotExist()

        composeRule.runOnIdle { zoom = zoom.copy(isCurrentValueVisible = true) }

        composeRule.onNodeWithContentDescription("1.8×").assertIsSelected()
        composeRule.onNodeWithContentDescription(".5×").assertExists()
        composeRule.onNodeWithContentDescription("2×").assertExists()
    }

    @Test
    fun aQuickTap_reportsThePressBeforeSelectingThePreset() {
        val actions = mutableListOf<Action>()
        composeRule.setContent {
            CameraTheme {
                PreviewZoom(
                    zoom = PreviewControlUiModel.Zoom(
                        range = 0.5f..8f,
                        presets = persistentListOf(0.5f, 1f, 2f),
                    ),
                    zoomRatio = { 1f },
                    onAction = { action -> actions += action },
                )
            }
        }

        composeRule.onNodeWithContentDescription("2×").performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(Action.ControlPressChanged(isPressed = true), actions.first())
            assertEquals(
                listOf(Action.PresetSelected(ratio = 2f)),
                actions.filterIsInstance<Action.PresetSelected>(),
            )
            assertEquals(
                listOf(true, false),
                actions.filterIsInstance<Action.ControlPressChanged>().map { action ->
                    action.isPressed
                },
            )
        }
    }

    @Test
    fun removingAPressedControl_clearsThePress() {
        var visible by mutableStateOf(true)
        val presses = mutableListOf<Boolean>()
        composeRule.setContent {
            CameraTheme {
                if (visible) {
                    PreviewZoom(
                        zoom = PreviewControlUiModel.Zoom(
                            range = 0.5f..8f,
                            presets = persistentListOf(0.5f, 1f, 2f),
                        ),
                        zoomRatio = { 1f },
                        onAction = { action ->
                            if (action is Action.ControlPressChanged) {
                                presses += action.isPressed
                            }
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("1×").performTouchInput { down(center) }
        composeRule.runOnIdle {
            assertEquals(true, presses.last())
            visible = false
        }
        composeRule.runOnIdle { assertEquals(false, presses.last()) }
    }
}
