package app.grapheneos.camera.ui.components.zoom

import androidx.compose.runtime.Immutable
import kotlin.math.max

@Immutable
internal class ZoomStopList(
    val stops: List<Float>,
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
}
