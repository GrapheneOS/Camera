package app.grapheneos.camera.ui.components.thumbnailbutton

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.progress.RingProgressIndicator
import app.grapheneos.camera.ui.components.progress.model.RingProgress

internal val RING_WIDTH = 2.dp

@Composable
internal fun ThumbnailButtonRing(
    progress: RingProgress,
    buttonShape: CornerBasedShape,
    color: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = progress,
        modifier = modifier.clearAndSetSemantics {
            progress.rangeInfo?.let { progressBarRangeInfo = it }
        },
        transitionSpec = { fadeIn() togetherWith fadeOut() using null },
        contentKey = { it::class },
    ) { shownProgress ->
        RingProgressIndicator(
            progress = shownProgress,
            modifier = Modifier.fillMaxSize(),
            shape = buttonShape,
            color = color,
            trackColor = Color.Transparent,
            strokeWidth = RING_WIDTH,
            strokeCap = StrokeCap.Round,
        )
    }
}
