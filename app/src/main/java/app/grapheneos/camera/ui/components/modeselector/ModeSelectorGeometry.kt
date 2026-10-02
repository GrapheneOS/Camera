package app.grapheneos.camera.ui.components.modeselector

import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

internal class ModeSelectorGeometry(
    itemWidths: List<Float>,
    private val containerWidth: Float,
    private val highlightInset: Float,
) {

    private val widths = itemWidths.toFloatArray()
    private val starts = itemWidths.runningFold(0f) { start, width -> start + width }.toFloatArray()
    private val centers = FloatArray(widths.size) { index -> starts[index] + widths[index] / 2 }

    fun itemStart(
        index: Int,
        position: Float,
    ): Int {
        return (containerWidth / 2 - center(position = position) + starts[index]).roundToInt()
    }

    fun width(position: Float): Float {
        return interpolate(
            values = widths,
            position = position,
        )
    }

    fun center(position: Float): Float {
        return interpolate(
            values = centers,
            position = position,
        )
    }

    fun position(center: Float): Float {
        if (centers.size < 2) {
            return 0f
        }

        val clamped = center.coerceIn(centers.first(), centers.last())
        val index = centers
            .indexOfLast { itemCenter -> itemCenter <= clamped }
            .coerceAtMost(centers.lastIndex - 1)

        return index + (clamped - centers[index]) / (centers[index + 1] - centers[index])
    }

    fun nearestIndex(position: Float): Int {
        return position.roundToInt().coerceAtMost(widths.lastIndex).coerceAtLeast(0)
    }

    fun highlightWidth(position: Float): Float {
        return width(position = position) - highlightInset * 2
    }

    fun highlightStartIn(
        index: Int,
        position: Float,
        isRtl: Boolean,
    ): Float {
        val highlight = highlightWidth(position = position)
        val itemStart = itemStart(
            index = index,
            position = position,
        ).toFloat()
        val itemLeft = when {
            isRtl -> containerWidth - itemStart - widths[index]
            else -> itemStart
        }

        return (containerWidth - highlight) / 2 - itemLeft
    }

    private fun interpolate(
        values: FloatArray,
        position: Float,
    ): Float {
        val from = position.toInt().coerceIn(0, values.lastIndex)
        val to = (from + 1).coerceAtMost(values.lastIndex)

        return lerp(
            start = values[from],
            stop = values[to],
            fraction = position - from,
        )
    }
}
