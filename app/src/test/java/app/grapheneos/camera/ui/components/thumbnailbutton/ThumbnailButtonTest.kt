package app.grapheneos.camera.ui.components.thumbnailbutton

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.grapheneos.camera.ui.components.progress.model.RingProgress
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThumbnailButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun thumbnailButton_withoutProgress_reportsNoProgress() {
        setContent(progress = RingProgress.None)

        composeRule.onNodeWithContentDescription(DESCRIPTION)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
    }

    @Test
    fun thumbnailButton_indeterminateProgress_isReportedOnTheButton() {
        setContent(progress = RingProgress.Indeterminate)

        composeRule.onNodeWithContentDescription(DESCRIPTION)
            .assertRangeInfoEquals(ProgressBarRangeInfo.Indeterminate)
    }

    @Test
    fun thumbnailButton_determinateProgress_isReportedOnTheButton() {
        setContent(progress = RingProgress.Determinate(fraction = { FRACTION }))

        composeRule.onNodeWithContentDescription(DESCRIPTION).assertRangeInfoEquals(
            ProgressBarRangeInfo(
                current = FRACTION,
                range = 0f..1f,
            ),
        )
    }

    private fun setContent(progress: RingProgress) {
        composeRule.setContent {
            CameraTheme {
                ThumbnailButton(
                    image = null,
                    onClick = {},
                    modifier = Modifier.semantics { contentDescription = DESCRIPTION },
                    progress = progress,
                )
            }
        }
    }

    private companion object {
        private const val DESCRIPTION = "Open gallery"
        private const val FRACTION = 0.4f
    }
}
