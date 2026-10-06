package app.grapheneos.camera.ui.components.progress

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.components.progress.model.RingProgress
import app.grapheneos.camera.ui.core.CameraPreviewColumn

private val DIAMETER = 40.dp
private val GAP_SIZE = 4.dp

private const val INDETERMINATE_LAP_MILLIS = 1_000
private const val FULL_TURN = 360f

@Composable
internal fun RingProgressIndicator(
    progress: RingProgress,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = CircleShape,
    color: Color = ProgressIndicatorDefaults.circularColor,
    trackColor: Color = ProgressIndicatorDefaults.circularDeterminateTrackColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
    gapSize: Dp = GAP_SIZE,
) {
    val rotation = LocalContentRotation.current
    val head = when (progress) {
        RingProgress.Indeterminate -> animateIndeterminateHead()
        else -> null
    }

    Spacer(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                progress.rangeInfo?.let { progressBarRangeInfo = it }
            }
            .size(size = DIAMETER)
            .drawWithCache {
                val stroke = Stroke(
                    width = strokeWidth.toPx(),
                    cap = strokeCap,
                )
                val drawing = RingDrawing(
                    ring = RingPath(
                        size = size,
                        strokeWidth = stroke.width,
                        cornerRadius = shape.topStart.toPx(size, this),
                    ),
                    stroke = stroke,
                    gap = gapSize.toPx(),
                    color = color,
                    trackColor = trackColor,
                )

                onDrawBehind {
                    with(drawing) {
                        drawProgress(
                            progress = progress,
                            head = head?.value ?: 0f,
                            start = rotation() / FULL_TURN,
                        )
                    }
                }
            },
    )
}

@Composable
private fun animateIndeterminateHead(): State<Float> {
    return rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = INDETERMINATE_LAP_MILLIS,
                easing = LinearEasing,
            ),
        ),
    )
}

@PreviewLightDark
@Composable
private fun RingProgressIndicatorPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewRingProgressRow(shape = CircleShape)
            PreviewRingProgressRow(shape = MaterialTheme.shapes.extraSmall)
        }
    }
}

@Composable
private fun PreviewRingProgressRow(
    shape: CornerBasedShape,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        RingProgressIndicator(
            progress = RingProgress.Segmented(
                segments = 3,
                fraction = { 2f / 3 },
            ),
            shape = shape,
        )
        RingProgressIndicator(
            progress = RingProgress.Segmented(
                segments = 10,
                fraction = { 0.75f },
            ),
            shape = shape,
        )
        RingProgressIndicator(
            progress = RingProgress.Segmented(
                segments = 10,
                fraction = { 0.75f },
            ),
            shape = shape,
            strokeCap = StrokeCap.Butt,
        )
        RingProgressIndicator(
            progress = RingProgress.Determinate(fraction = { 0.6f }),
            shape = shape,
        )
        RingProgressIndicator(
            progress = RingProgress.Indeterminate,
            shape = shape,
        )
    }
}
