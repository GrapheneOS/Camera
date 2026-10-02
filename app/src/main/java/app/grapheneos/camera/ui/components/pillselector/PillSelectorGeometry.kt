package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.util.lerp
import kotlin.math.ceil
import kotlin.math.floor

internal class PillSelectorGeometry(
    itemCount: Int,
) {

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

internal fun DrawScope.drawPillSelectorHighlight(
    selection: Float,
    geometry: PillSelectorGeometry,
    color: Color,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(
            x = geometry.start(selection = selection),
            y = 0f,
        ),
        size = Size(
            width = geometry.width(selection = selection),
            height = size.height,
        ),
        cornerRadius = CornerRadius(size.height / 2),
    )
}
