package app.grapheneos.camera.ui.components.progress

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SegmentedCircularProgressIndicatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun segmentedIndicator_reportsItsProgress() {
        composeRule.setContent {
            CameraTheme {
                SegmentedCircularProgressIndicator(
                    segments = 4,
                    progress = { 0.75f },
                    modifier = Modifier.testTag(TAG),
                )
            }
        }

        composeRule.onNodeWithTag(TAG).assertRangeInfoEquals(
            ProgressBarRangeInfo(
                current = 0.75f,
                range = 0f..1f,
            ),
        )
    }

    private companion object {
        private const val TAG = "indicator"
    }
}
