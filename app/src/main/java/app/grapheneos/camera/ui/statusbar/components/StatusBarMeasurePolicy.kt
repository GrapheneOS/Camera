package app.grapheneos.camera.ui.statusbar.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val EDGE_PADDING = 8.dp
private val BOTTOM_PADDING = 4.dp
private val ITEM_OVERLAP = 8.dp

// Items go from the start up to a cutout in the middle of the bar, and stop at the first one that
// does not fit, so an item never takes the place of one before it.
internal class StatusBarMeasurePolicy(
    private val cutout: Rect,
) : MultiContentMeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints,
    ): MeasureResult {
        val (items, settings) = measurables
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val edgePadding = EDGE_PADDING.roundToPx()
        val itemOverlap = ITEM_OVERLAP.roundToPx()
        val settingsPlaceable = settings.single().measure(Constraints())
        val itemPlaceables = items.map { item -> item.measure(Constraints()) }
        val rowTop = height - BOTTOM_PADDING.roundToPx() - settingsPlaceable.height
        val settingsStart = width - edgePadding - settingsPlaceable.width
        val cutoutInRow = cutout.width > 0 && cutout.bottom > rowTop
        val (cutoutStart, cutoutEnd) = when (layoutDirection) {
            LayoutDirection.Ltr -> cutout.left to cutout.right
            LayoutDirection.Rtl -> width - cutout.right to width - cutout.left
        }
        val itemsStart = when {
            cutoutInRow && cutoutStart <= edgePadding -> cutoutEnd.roundToInt()
            else -> edgePadding
        }
        val itemsEnd = when {
            cutoutInRow && cutoutStart > itemsStart -> {
                minOf(cutoutStart.roundToInt(), settingsStart)
            }

            else -> settingsStart
        }
        var itemStart = itemsStart
        val shownItems = itemPlaceables.takeWhile { placeable ->
            val fits = itemStart + placeable.width <= itemsEnd

            itemStart += placeable.width - itemOverlap
            fits
        }

        return layout(width = width, height = height) {
            var x = itemsStart

            shownItems.forEach { placeable ->
                placeable.placeRelative(
                    x = x,
                    y = rowTop + (settingsPlaceable.height - placeable.height) / 2,
                )
                x += placeable.width - itemOverlap
            }
            settingsPlaceable.placeRelative(
                x = settingsStart,
                y = rowTop,
            )
        }
    }
}
