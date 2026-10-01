package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonProgress
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fixture = CaptureButtonFixture(composeRule = composeRule)

    @Test
    fun captureButton_tappedInQuickSuccession_callsOnClickForEveryTap() {
        fixture.setContent()

        fixture.button().performClick()
        fixture.button().performClick()
        fixture.button().performClick()

        assertEquals(3, fixture.clicks)
    }

    @Test
    fun captureButton_pressTrigger_clicksAsSoonAsPressed() {
        fixture.trigger = CaptureButtonTrigger.Press
        fixture.setContent()

        fixture.button().performTouchInput { down(center) }
        assertEquals(1, fixture.clicks)

        fixture.button().performTouchInput { up() }
        assertEquals(1, fixture.clicks)
    }

    @Test
    fun captureButton_releaseTrigger_waitsForTheRelease() {
        fixture.setContent()

        fixture.button().performTouchInput { down(center) }
        assertEquals(0, fixture.clicks)

        fixture.button().performTouchInput { up() }
        assertEquals(1, fixture.clicks)
    }

    @Test
    fun captureButton_releasedOutside_doesNotClick() {
        fixture.setContent()

        fixture.button().performTouchInput {
            down(center)
            moveTo(Offset(x = -width.toFloat(), y = -height.toFloat()))
            up()
        }

        assertEquals(0, fixture.clicks)
    }

    @Test
    fun captureButton_touchCancelled_doesNotClick() {
        fixture.setContent()

        fixture.button().performTouchInput {
            down(center)
            cancel()
        }

        assertEquals(0, fixture.clicks)
    }

    @Test
    fun captureButton_enterPressed_clicks() {
        fixture.setContent()

        fixture.button().requestFocus()
        fixture.button().performKeyInput { pressKey(Key.Enter) }

        assertEquals(1, fixture.clicks)
    }

    @Test
    fun captureButton_disabled_ignoresTaps() {
        fixture.enabled = false
        fixture.setContent()

        fixture.button().performClick()

        fixture.button().assertIsNotEnabled()
        assertEquals(0, fixture.clicks)
    }

    @Test
    fun captureButton_isAnnouncedAsAButton() {
        fixture.setContent()

        fixture.button().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun captureButton_withAnIcon_announcesOnlyTheCallersDescription() {
        fixture.icon = TEST_ICON
        fixture.setContent()

        fixture.button().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(CaptureButtonFixture.DESCRIPTION),
            ),
        )
    }

    @Test
    fun captureButton_indeterminateProgress_isReportedOnTheButton() {
        fixture.progress = CaptureButtonProgress.Indeterminate
        fixture.setContent()

        fixture.button().assertRangeInfoEquals(ProgressBarRangeInfo.Indeterminate)
    }

    @Test
    fun captureButton_segmentedProgress_isReportedOnTheButton() {
        fixture.progress = CaptureButtonProgress.Segmented(
            segments = 10,
            fraction = { 0.7f },
        )
        fixture.setContent()

        fixture.button().assertRangeInfoEquals(
            ProgressBarRangeInfo(
                current = 0.7f,
                range = 0f..1f,
            ),
        )
    }

    @Test
    fun captureButton_fractionChanged_reportsTheNewProgress() {
        var fraction by mutableFloatStateOf(0.2f)
        fixture.progress = CaptureButtonProgress.Determinate(fraction = { fraction })
        fixture.setContent()

        fraction = 0.8f

        fixture.button().assertRangeInfoEquals(
            ProgressBarRangeInfo(
                current = 0.8f,
                range = 0f..1f,
            ),
        )
    }

    @Test
    fun captureButton_progressRemoved_stopsReportingProgress() {
        fixture.progress = CaptureButtonProgress.Indeterminate
        fixture.setContent()

        fixture.progress = CaptureButtonProgress.None

        fixture.button().assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo),
        )
    }
}
