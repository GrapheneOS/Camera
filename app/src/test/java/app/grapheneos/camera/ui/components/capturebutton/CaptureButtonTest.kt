package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonCore
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonProgress
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0

    @Test
    fun captureButton_tapped_callsOnClick() {
        setCaptureButton()

        button().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun captureButton_tappedInQuickSuccession_callsOnClickForEveryTap() {
        setCaptureButton()

        button().performClick()
        button().performClick()
        button().performClick()

        assertEquals(3, clicks)
    }

    @Test
    fun captureButton_pressTrigger_clicksAsSoonAsPressed() {
        setCaptureButton(trigger = CaptureButtonTrigger.Press)

        button().performTouchInput { down(center) }
        assertEquals(1, clicks)

        button().performTouchInput { up() }
        assertEquals(1, clicks)
    }

    @Test
    fun captureButton_releaseTrigger_waitsForTheRelease() {
        setCaptureButton(trigger = CaptureButtonTrigger.Release)

        button().performTouchInput { down(center) }
        assertEquals(0, clicks)

        button().performTouchInput { up() }
        assertEquals(1, clicks)
    }

    @Test
    fun captureButton_releasedOutside_doesNotClick() {
        setCaptureButton()

        button().performTouchInput {
            down(center)
            moveTo(Offset(x = -width.toFloat(), y = -height.toFloat()))
            up()
        }

        assertEquals(0, clicks)
    }

    @Test
    fun captureButton_enterPressed_clicks() {
        setCaptureButton()

        button().requestFocus()
        button().performKeyInput { pressKey(Key.Enter) }

        assertEquals(1, clicks)
    }

    @Test
    fun captureButton_disabled_ignoresTaps() {
        setCaptureButton(enabled = false)

        button().performClick()

        button().assertIsNotEnabled()
        assertEquals(0, clicks)
    }

    @Test
    fun captureButton_isAnnouncedAsAButton() {
        setCaptureButton()

        button().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun captureButton_withAnIcon_announcesOnlyTheCallersDescription() {
        setCaptureButton(icon = ICON)

        button().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(DESCRIPTION),
            ),
        )
    }

    @Test
    fun captureButton_indeterminateProgress_isReportedOnTheButton() {
        setCaptureButton(progress = CaptureButtonProgress.Indeterminate)

        button().assertRangeInfoEquals(ProgressBarRangeInfo.Indeterminate)
    }

    @Test
    fun captureButton_determinateProgress_isReportedOnTheButton() {
        setCaptureButton(progress = CaptureButtonProgress.Determinate(fraction = 0.6f))

        button().assertRangeInfoEquals(
            ProgressBarRangeInfo(
                current = 0.6f,
                range = 0f..1f,
            ),
        )
    }

    @Test
    fun captureButton_segmentedProgress_isReportedInSteps() {
        setCaptureButton(
            progress = CaptureButtonProgress.Segmented(
                segments = 10,
                filled = 7,
            ),
        )

        button().assertRangeInfoEquals(
            ProgressBarRangeInfo(
                current = 7f,
                range = 0f..10f,
                steps = 9,
            ),
        )
    }

    @Test
    fun captureButton_progressChanged_reportsTheNewProgress() {
        var progress: CaptureButtonProgress by mutableStateOf(
            CaptureButtonProgress.Determinate(fraction = 0.2f),
        )
        composeRule.setContent {
            CameraTheme {
                CaptureButton(
                    onClick = {},
                    core = CaptureButtonCore.Disc,
                    modifier = Modifier.semantics { contentDescription = DESCRIPTION },
                    progress = progress,
                )
            }
        }

        progress = CaptureButtonProgress.Determinate(fraction = 0.8f)

        button().assertRangeInfoEquals(
            ProgressBarRangeInfo(
                current = 0.8f,
                range = 0f..1f,
            ),
        )
    }

    @Test
    fun captureButton_progressRemoved_stopsReportingProgress() {
        var progress: CaptureButtonProgress by mutableStateOf(CaptureButtonProgress.Indeterminate)
        composeRule.setContent {
            CameraTheme {
                CaptureButton(
                    onClick = {},
                    core = CaptureButtonCore.Disc,
                    modifier = Modifier.semantics { contentDescription = DESCRIPTION },
                    progress = progress,
                )
            }
        }

        progress = CaptureButtonProgress.None

        button().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
    }

    private fun setCaptureButton(
        enabled: Boolean = true,
        progress: CaptureButtonProgress = CaptureButtonProgress.None,
        trigger: CaptureButtonTrigger = CaptureButtonTrigger.Release,
        icon: ImageVector? = null,
    ) {
        composeRule.setContent {
            CameraTheme {
                CaptureButton(
                    onClick = { clicks += 1 },
                    core = CaptureButtonCore.Disc,
                    modifier = Modifier.semantics { contentDescription = DESCRIPTION },
                    enabled = enabled,
                    progress = progress,
                    trigger = trigger,
                    icon = icon,
                )
            }
        }
    }

    private fun button(): SemanticsNodeInteraction {
        return composeRule.onNodeWithContentDescription(DESCRIPTION)
    }

    private companion object {
        private const val DESCRIPTION = "Capture"

        private val ICON = ImageVector
            .Builder(
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
            .build()
    }
}
