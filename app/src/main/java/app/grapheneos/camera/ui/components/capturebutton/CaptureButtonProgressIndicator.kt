package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.components.motion.crossfade
import app.grapheneos.camera.ui.components.progress.RingProgressIndicator
import app.grapheneos.camera.ui.components.progress.model.RingProgress

private val PROGRESS_PADDING = 4.dp
private val PROGRESS_GAP = 4.dp

internal val PROGRESS_INSET = PROGRESS_PADDING +
    ProgressIndicatorDefaults.CircularStrokeWidth +
    PROGRESS_GAP

@Composable
internal fun CaptureButtonProgressIndicator(
    progress: RingProgress,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val rotation = LocalContentRotation.current
    val turned = Modifier
        .fillMaxSize()
        .graphicsLayer { rotationZ = rotation() }

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
        transitionSpec = { crossfade() },
        contentKey = { it::class },
    ) { targetProgress ->
        when (targetProgress) {
            RingProgress.None -> Unit

            RingProgress.Indeterminate -> {
                CircularProgressIndicator(
                    modifier = turned,
                    color = color,
                )
            }

            is RingProgress.Determinate -> {
                CircularProgressIndicator(
                    progress = targetProgress.fraction,
                    modifier = turned,
                    color = color,
                    trackColor = trackColor,
                )
            }

            is RingProgress.Segmented -> {
                RingProgressIndicator(
                    progress = targetProgress,
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    trackColor = trackColor,
                )
            }
        }
    }
}
