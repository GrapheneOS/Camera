package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonProgress
import app.grapheneos.camera.ui.components.progress.SegmentedCircularProgressIndicator

private val PROGRESS_PADDING = 4.dp
private val PROGRESS_GAP = 4.dp

internal val PROGRESS_INSET = PROGRESS_PADDING +
    ProgressIndicatorDefaults.CircularStrokeWidth +
    PROGRESS_GAP

@Composable
internal fun CaptureButtonProgressIndicator(
    progress: CaptureButtonProgress,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = progress,
        modifier = modifier
            .padding(all = PROGRESS_PADDING)
            .clearAndSetSemantics {
                val rangeInfo = progress.rangeInfo
                if (rangeInfo != null) {
                    progressBarRangeInfo = rangeInfo
                }
            },
        transitionSpec = { fadeIn() togetherWith fadeOut() using null },
        contentKey = { it::class },
    ) { targetProgress ->
        when (targetProgress) {
            CaptureButtonProgress.None -> Unit

            CaptureButtonProgress.Indeterminate -> {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                )
            }

            is CaptureButtonProgress.Determinate -> {
                CircularProgressIndicator(
                    progress = targetProgress.fraction,
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    trackColor = trackColor,
                )
            }

            is CaptureButtonProgress.Segmented -> {
                SegmentedCircularProgressIndicator(
                    segments = targetProgress.segments,
                    progress = targetProgress.fraction,
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    trackColor = trackColor,
                )
            }
        }
    }
}
