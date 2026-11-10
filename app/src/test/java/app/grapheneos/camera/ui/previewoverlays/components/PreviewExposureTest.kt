package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import app.grapheneos.camera.ui.core.theme.CameraTheme
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.ExposureAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewControlUiModel
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewExposureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val onAction = mockk<(Action) -> Unit>(relaxed = true)
    private val exposure = PreviewControlUiModel.Exposure(
        value = 0.5f,
        range = -2f..2f,
        steps = 23,
        majorTickInterval = 6,
    )

    @Test
    fun draggingTheScale_reportsTheChangeBeforeRelease() {
        showExposure()
        val bar = composeRule.onNodeWithContentDescription("Exposure compensation")

        bar.performTouchInput {
            down(center)
            repeat(2) {
                moveBy(
                    Offset(
                        x = -100f,
                        y = 0f,
                    ),
                )
            }
        }

        verify { onAction(Action.PressChanged(isPressed = true)) }
        verify { onAction(match { it is Action.ValueChanged && it.value > exposure.value }) }
        verify(exactly = 0) { onAction(Action.PressChanged(isPressed = false)) }

        bar.performTouchInput { up() }

        verify(exactly = 1) { onAction(Action.PressChanged(isPressed = false)) }
    }

    private fun showExposure() {
        composeRule.setContent {
            CameraTheme {
                PreviewExposure(
                    exposure = exposure,
                    onAction = onAction,
                )
            }
        }
    }
}
