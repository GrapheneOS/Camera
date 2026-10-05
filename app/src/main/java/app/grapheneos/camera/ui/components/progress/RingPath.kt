package app.grapheneos.camera.ui.components.progress

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure

internal class RingPath(
    size: Size,
    strokeWidth: Float,
    cornerRadius: Float,
) {

    private val measure = PathMeasure()

    val length: Float

    init {
        measure.setPath(
            path = centerLine(
                size = size,
                inset = strokeWidth / 2,
                radius = (cornerRadius - strokeWidth / 2).coerceAtLeast(0f),
            ),
            forceClosed = false,
        )
        length = measure.length
    }

    fun segment(
        start: Float,
        sweep: Float,
        destination: Path,
    ) {
        val from = start.mod(length)
        val to = from + sweep

        destination.reset()
        measure.getSegment(
            startDistance = from,
            stopDistance = minOf(to, length),
            destination = destination,
        )
        if (to > length) {
            measure.getSegment(
                startDistance = 0f,
                stopDistance = to - length,
                destination = destination,
            )
        }
    }

    fun position(distance: Float): Offset {
        return measure.getPosition(distance = distance.mod(length))
    }

    private companion object {
        private const val QUARTER_TURN = 90f

        private fun centerLine(
            size: Size,
            inset: Float,
            radius: Float,
        ): Path {
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            val diameter = radius * 2

            return Path().apply {
                moveTo(x = size.width / 2, y = top)
                arcTo(
                    rect = Rect(
                        left = right - diameter,
                        top = top,
                        right = right,
                        bottom = top + diameter,
                    ),
                    startAngleDegrees = -QUARTER_TURN,
                    sweepAngleDegrees = QUARTER_TURN,
                    forceMoveTo = false,
                )
                arcTo(
                    rect = Rect(
                        left = right - diameter,
                        top = bottom - diameter,
                        right = right,
                        bottom = bottom,
                    ),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = QUARTER_TURN,
                    forceMoveTo = false,
                )
                arcTo(
                    rect = Rect(
                        left = left,
                        top = bottom - diameter,
                        right = left + diameter,
                        bottom = bottom,
                    ),
                    startAngleDegrees = QUARTER_TURN,
                    sweepAngleDegrees = QUARTER_TURN,
                    forceMoveTo = false,
                )
                arcTo(
                    rect = Rect(
                        left = left,
                        top = top,
                        right = left + diameter,
                        bottom = top + diameter,
                    ),
                    startAngleDegrees = 2 * QUARTER_TURN,
                    sweepAngleDegrees = QUARTER_TURN,
                    forceMoveTo = false,
                )
                close()
            }
        }
    }
}
