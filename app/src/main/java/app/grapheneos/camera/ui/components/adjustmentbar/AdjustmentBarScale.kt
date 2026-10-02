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

    override val lastTick: Int = steps + 1

    override fun isMajor(tick: Int): Boolean {
        return tick % majorTickInterval == 0
    }

    override fun position(value: Float): Float {
        val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)

        return fraction.coerceIn(0f, 1f) * lastTick
    }

    override fun value(position: Float): Float {
        return valueRange.start + (valueRange.endInclusive - valueRange.start) * position / lastTick
    }

    fun tickOf(value: Float): Int {
        return nearestTick(position = position(value = value))
    }

    fun nearestTick(position: Float): Int {
        return position.roundToInt().coerceIn(0, lastTick)
    }
}
