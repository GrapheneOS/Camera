package app.grapheneos.camera.ui.components.zoom

import androidx.compose.runtime.Immutable
import kotlin.math.max

@Immutable
internal class ZoomStopList(
    private val stops: List<Float>,
) {

    init {
        require(stops.isNotEmpty()) {
            "stops must not be empty"
        }
        require(stops.zipWithNext().all { (previous, next) -> previous < next }) {
            "stops must be ascending, were $stops"
        }
    }

    fun selectedIndex(value: Float): Int {
        return max(0, stops.indexOfLast { stop -> stop <= value })
    }

    fun keyOf(stop: Float): Key {
        return when {
            stop == stops.first() && stop < 1f -> Key.WideAngle
            else -> Key.Ratio(value = stop)
        }
    }

    sealed interface Key {

        data object WideAngle : Key

        data class Ratio(
            val value: Float,
        ) : Key
    }
}
