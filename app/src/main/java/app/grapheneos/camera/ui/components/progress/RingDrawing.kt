package app.grapheneos.camera.ui.components.progress

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.grapheneos.camera.ui.components.progress.model.RingProgress

internal class RingDrawing(
    private val ring: RingPath,
    private val stroke: Stroke,
    private val gap: Float,
    private val color: Color,
    private val trackColor: Color,
) {

    private val scratch = Path()

    private val cap = when (stroke.cap) {
        StrokeCap.Butt -> 0f
        else -> stroke.width / 2
    }

    fun DrawScope.drawProgress(
        progress: RingProgress,
        head: Float,
    ) {
        when (progress) {
            RingProgress.None -> Unit

            RingProgress.Indeterminate -> drawRingArc(
                start = head * ring.length,
                sweep = INDETERMINATE_SWEEP * ring.length,
                color = color,
            )

            is RingProgress.Determinate -> drawSegments(
                segments = 1,
                filledSegments = progress.fraction().coerceIn(0f, 1f),
                gap = 0f,
            )

            is RingProgress.Segmented -> drawSegments(
                segments = progress.segments,
                filledSegments = progress.fraction().coerceIn(0f, 1f) * progress.segments,
                gap = gap,
            )
        }
    }

    private fun DrawScope.drawSegments(
        segments: Int,
        filledSegments: Float,
        gap: Float,
    ) {
        val segmentLength = ring.length / segments
        val sweep = (segmentLength - gap - cap * 2).coerceAtLeast(0f)

        repeat(segments) { index ->
            val start = segmentLength * index + gap / 2 + cap
            val fill = (filledSegments - index).coerceIn(0f, 1f)

            drawRingArc(
                start = start,
                sweep = sweep,
                color = trackColor,
            )
            if (fill > 0f) {
                val filled = SegmentFill.of(
                    fill = fill,
                    sweep = sweep,
                    cap = cap,
                )

                drawRingArc(
                    start = start,
                    sweep = filled.sweep,
                    color = color.copy(alpha = color.alpha * filled.alpha),
                )
            }
        }
    }

    private fun DrawScope.drawRingArc(
        start: Float,
        sweep: Float,
        color: Color,
    ) {
        when {
            sweep > 0f -> {
                ring.segment(
                    start = start,
                    sweep = sweep,
                    destination = scratch,
                )
                drawPath(
                    path = scratch,
                    color = color,
                    style = stroke,
                )
            }

            cap > 0f -> drawCircle(
                color = color,
                radius = cap,
                center = ring.position(distance = start),
            )
        }
    }

    private companion object {
        private const val INDETERMINATE_SWEEP = 0.25f
    }
}
