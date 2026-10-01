package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

@Immutable
internal data class AdjustmentBarScale(
    val valueRange: ClosedFloatingPointRange<Float>,
    val steps: Int,
) {

    val lastTick: Int = steps + 1

    init {
        require(steps > 0) {
            "steps must be positive, was $steps"
        }
        require(valueRange.endInclusive > valueRange.start) {
            "valueRange must not be empty, was $valueRange"
        }
    }

    fun tickOf(value: Float): Int {
        val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)

        return nearestTick(position = fraction * lastTick)
    }

    fun nearestTick(position: Float): Int {
        return position.roundToInt().coerceIn(0, lastTick)
    }

    fun value(tick: Int): Float {
        return valueRange.start + (valueRange.endInclusive - valueRange.start) * tick / lastTick
    }
}
