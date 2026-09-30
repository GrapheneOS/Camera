package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonProgress
import app.grapheneos.camera.ui.components.progress.SegmentedCircularProgressIndicator

@Composable
internal fun CaptureButtonProgressIndicator(
    progress: CaptureButtonProgress,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = progress,
        modifier = modifier.clearAndSetSemantics {
            val rangeInfo = progress.rangeInfo
            if (rangeInfo != null) {
                progressBarRangeInfo = rangeInfo
            }
        },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
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
                val fraction by animateFraction(targetProgress.fraction)

                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    trackColor = trackColor,
                )
            }

            is CaptureButtonProgress.Segmented -> {
                val fraction by animateFraction(targetProgress.fraction)

                SegmentedCircularProgressIndicator(
                    segments = targetProgress.segments,
                    progress = { fraction },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    trackColor = trackColor,
                )
            }
        }
    }
}

@Composable
private fun animateFraction(fraction: Float): State<Float> {
    return animateFloatAsState(
        targetValue = fraction,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
    )
}
