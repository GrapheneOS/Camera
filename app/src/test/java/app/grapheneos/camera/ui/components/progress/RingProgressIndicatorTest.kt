package app.grapheneos.camera.ui.components.progress

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.grapheneos.camera.ui.components.progress.model.RingProgress
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RingProgressIndicatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun segmentedIndicator_reportsItsProgress() {
        setContent(
            progress = RingProgress.Segmented(
                segments = 4,
                fraction = { 0.75f },
            ),
        )

        composeRule.onNodeWithTag(TAG).assertRangeInfoEquals(
            ProgressBarRangeInfo(
                current = 0.75f,
                range = 0f..1f,
            ),
        )
    }

    @Test
    fun indeterminateIndicator_isReportedAsIndeterminate() {
        setContent(progress = RingProgress.Indeterminate)

        composeRule.onNodeWithTag(TAG).assertRangeInfoEquals(ProgressBarRangeInfo.Indeterminate)
    }

    @Test
    fun noProgress_reportsNothing() {
        setContent(progress = RingProgress.None)

        composeRule.onNodeWithTag(TAG)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
    }

    private fun setContent(progress: RingProgress) {
        composeRule.setContent {
            CameraTheme {
                RingProgressIndicator(
                    progress = progress,
                    modifier = Modifier.testTag(TAG),
                )
            }
        }
    }

    private companion object {
        private const val TAG = "indicator"
    }
}
