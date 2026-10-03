package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.ui.util.lerp
import kotlin.math.ceil
import kotlin.math.floor

internal class PillSelectorGeometry(
    itemCount: Int,
) {

    val lastIndex: Int
        get() {
            return starts.lastIndex
        }

    private val starts = FloatArray(itemCount)
    private val widths = FloatArray(itemCount)

    fun place(
        index: Int,
        start: Float,
        width: Float,
    ) {
        starts[index] = start
        widths[index] = width
    }

    fun start(selection: Float): Float {
        return interpolate(
            values = starts,
            selection = selection,
        )
    }

    fun width(selection: Float): Float {
        return interpolate(
            values = widths,
            selection = selection,
        )
    }

    fun itemStart(index: Int): Float {
        return starts[index]
    }

    fun pitch(selection: Float): Float {
        val from = floor(selection).toInt().coerceIn(0, lastIndex - 1)

        return starts[from + 1] - starts[from]
    }

    private fun interpolate(
        values: FloatArray,
        selection: Float,
    ): Float {
        val from = floor(selection).toInt().coerceIn(0, values.lastIndex)
        val to = ceil(selection).toInt().coerceIn(0, values.lastIndex)

        return lerp(
            start = values[from],
            stop = values[to],
            fraction = selection - from,
        )
    }
}
