package app.grapheneos.camera.ui.components.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn

private val DIAMETER = 40.dp
private val GAP_SIZE = 4.dp

private const val START_ANGLE = -90f
private const val FULL_CIRCLE = 360f

@Composable
internal fun SegmentedCircularProgressIndicator(
    segments: Int,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    trackColor: Color = ProgressIndicatorDefaults.circularDeterminateTrackColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
    gapSize: Dp = GAP_SIZE,
) {
    require(segments > 0) {
        "segments must be positive, was $segments"
    }

    Canvas(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = progress().coerceIn(0f, 1f),
                    range = 0f..1f,
                )
            }
            .size(DIAMETER),
    ) {
        drawSegments(
            segments = segments,
            filledSegments = progress().coerceIn(0f, 1f) * segments,
            color = color,
            trackColor = trackColor,
            stroke = Stroke(
                width = strokeWidth.toPx(),
                cap = strokeCap,
            ),
            gap = gapSize.toPx(),
        )
    }
}

private fun DrawScope.drawSegments(
    segments: Int,
    filledSegments: Float,
    color: Color,
    trackColor: Color,
    stroke: Stroke,
    gap: Float,
) {
    val radius = (size.minDimension - stroke.width) / 2
    val capAngle = when (stroke.cap) {
        StrokeCap.Butt -> 0f
        else -> degrees(radians = stroke.width / 2 / radius)
    }
    val gapAngle = degrees(radians = gap / radius)
    val segmentAngle = FULL_CIRCLE / segments
    val sweepAngle = (segmentAngle - gapAngle - capAngle * 2).coerceAtLeast(0f)

    repeat(segments) { index ->
        val startAngle = START_ANGLE + segmentAngle * index + gapAngle / 2 + capAngle
        val fill = (filledSegments - index).coerceIn(0f, 1f)

        drawSegment(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            radius = radius,
            stroke = stroke,
        )
        if (fill > 0f) {
            drawSegment(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle * fill,
                radius = radius,
                stroke = stroke,
            )
        }
    }
}

private fun DrawScope.drawSegment(
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
    radius: Float,
    stroke: Stroke,
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = center - Offset(x = radius, y = radius),
        size = Size(width = radius * 2, height = radius * 2),
        style = stroke,
    )
}

private fun degrees(radians: Float): Float {
    return Math.toDegrees(radians.toDouble()).toFloat()
}

@PreviewLightDark
@Composable
private fun SegmentedCircularProgressIndicatorPreview() {
    CameraPreviewColumn {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            SegmentedCircularProgressIndicator(
                segments = 3,
                progress = { 2f / 3 },
            )
            SegmentedCircularProgressIndicator(
                segments = 10,
                progress = { 0.75f },
            )
            SegmentedCircularProgressIndicator(
                segments = 10,
                progress = { 0.75f },
                strokeCap = StrokeCap.Butt,
            )
        }
    }
}
