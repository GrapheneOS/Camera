package app.grapheneos.camera.ui.components.ruler

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

@Immutable
internal class RulerMetrics(
    density: Density,
) {

    val tickSpacing = with(density) { TICK_SPACING.toPx() }
    val minorTickHeight = with(density) { MINOR_TICK_HEIGHT.toPx() }
    val majorTickHeight = with(density) { MAJOR_TICK_HEIGHT.toPx() }
    val tickWidth = with(density) { TICK_WIDTH.toPx() }
    val indicatorSize = with(density) { Size(INDICATOR_WIDTH.toPx(), INDICATOR_HEIGHT.toPx()) }
    val fadeWidth = with(density) { FADE_WIDTH.toPx() }
    val focusStroke = Stroke(width = with(density) { FOCUS_RING_WIDTH.toPx() })

    private companion object {
        private val TICK_SPACING = 8.75.dp
        private val MINOR_TICK_HEIGHT = 8.dp
        private val MAJOR_TICK_HEIGHT = 14.dp
        private val TICK_WIDTH = 1.5.dp
        private val INDICATOR_WIDTH = 4.dp
        private val INDICATOR_HEIGHT = 18.dp
        private val FADE_WIDTH = 56.dp
        private val FOCUS_RING_WIDTH = 3.dp
    }
}
