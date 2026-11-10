package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import app.grapheneos.camera.ui.core.theme.CameraTheme
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.ExposureAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewControlUiModel
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val onAction = mockk<(Action) -> Unit>(relaxed = true)

    @Test
    fun exposureAndReset_reportTheirActionsInEv() {
        composeRule.setContent {
            CameraTheme {
                PreviewControls(
                    control = PreviewControlUiModel.Exposure(
                        value = 0.5f,
                        range = -2f..2f,
                        steps = 23,
                        majorTickInterval = 6,
                        isResetVisible = true,
                    ),
                    onAction = onAction,
                )
            }
        }

        composeRule.onNodeWithText("0.5 EV").assertDoesNotExist()
        val bar = composeRule.onNodeWithContentDescription("Exposure compensation")
        bar.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0.5 EV"),
        )
        bar.assertRangeInfoEquals(
            ProgressBarRangeInfo(
                current = 0.5f,
                range = -2f..2f,
                steps = 23,
            ),
        )
        bar.performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(1f) }
        composeRule.onNodeWithContentDescription("Reset exposure compensation").performClick()

        verifyOrder {
            onAction(ExposureAction.ValueChanged(value = 1f))
            onAction(ExposureAction.ResetClicked)
        }
    }

    @Test
    fun resetAppearing_keepsATwoValueScaleCenteredInTheSameBounds() {
        var isAdjusted by mutableStateOf(false)
        composeRule.setContent {
            CameraTheme {
                PreviewControls(
                    control = PreviewControlUiModel.Exposure(
                        value = 0f,
                        range = 0f..1f,
                        steps = 0,
                        majorTickInterval = 1,
                        isResetVisible = isAdjusted,
                    ),
                    onAction = {},
                    modifier = Modifier.testTag("controls"),
                )
            }
        }
        val bar = composeRule.onNodeWithContentDescription("Exposure compensation")
        val before = bar.fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithContentDescription("Reset exposure compensation").assertDoesNotExist()

        composeRule.runOnIdle { isAdjusted = true }

        val container = composeRule.onNodeWithTag("controls").fetchSemanticsNode().boundsInRoot
        assertEquals(before, bar.fetchSemanticsNode().boundsInRoot)
        assertEquals(container.center.x, before.center.x)
        composeRule.onNodeWithContentDescription("Reset exposure compensation").assertExists()
    }
}
