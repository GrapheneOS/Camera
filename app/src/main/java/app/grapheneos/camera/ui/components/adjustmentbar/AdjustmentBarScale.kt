package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.ui.components.ruler.RulerScale
import kotlin.math.roundToInt

@Immutable
internal data class AdjustmentBarScale(
    val valueRange: ClosedFloatingPointRange<Float>,
    val steps: Int,
    val majorTickInterval: Int,
) : RulerScale {

    override val lastTick: Int = steps + 1

    init {
        require(steps > 0) {
            "steps must be positive, was $steps"
        }
        require(valueRange.endInclusive > valueRange.start) {
            "valueRange must not be empty, was $valueRange"
        }
        require(majorTickInterval > 0) {
            "majorTickInterval must be positive, was $majorTickInterval"
        }
    }

    override fun isMajor(tick: Int): Boolean {
        return tick % majorTickInterval == 0
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
