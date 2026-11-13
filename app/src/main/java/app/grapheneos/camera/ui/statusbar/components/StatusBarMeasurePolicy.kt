package app.grapheneos.camera.ui.statusbar.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val EDGE_PADDING = 8.dp
private val BOTTOM_PADDING = 4.dp
private val ITEM_OVERLAP = 8.dp

// Items go from the start up to a cutout, and stop at the first one that does not fit, so an item
// never takes the place of one before it.
internal class StatusBarMeasurePolicy(
    private val cutout: Rect,
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val edgePadding = EDGE_PADDING.roundToPx()
        val itemOverlap = ITEM_OVERLAP.roundToPx()
        val itemPlaceables = measurables.map { item -> item.measure(Constraints()) }
        val rowHeight = itemPlaceables.maxOfOrNull { placeable -> placeable.height } ?: 0
        val rowTop = height - BOTTOM_PADDING.roundToPx() - rowHeight
        val cutoutSpan = cutoutSpan(
            width = width,
            rowTop = rowTop,
        )
        val itemsStart = when {
            cutoutSpan != null && cutoutSpan.first <= edgePadding -> cutoutSpan.last
            else -> edgePadding
        }
        val barEnd = width - edgePadding
        val itemsEnd = when {
            cutoutSpan != null && cutoutSpan.first > itemsStart -> minOf(cutoutSpan.first, barEnd)
            else -> barEnd
        }
        val shownItems = fittingItems(
            placeables = itemPlaceables,
            start = itemsStart,
            end = itemsEnd,
            overlap = itemOverlap,
        )

        return layout(width = width, height = height) {
            var x = itemsStart

            shownItems.forEach { placeable ->
                placeable.placeRelative(
                    x = x,
                    y = rowTop + (rowHeight - placeable.height) / 2,
                )
                x += placeable.width - itemOverlap
            }
        }
    }

    private fun MeasureScope.cutoutSpan(
        width: Int,
        rowTop: Int,
    ): IntRange? {
        if (cutout.width <= 0f || cutout.bottom <= rowTop) return null

        val (start, end) = when (layoutDirection) {
            LayoutDirection.Ltr -> cutout.left to cutout.right
            LayoutDirection.Rtl -> width - cutout.right to width - cutout.left
        }

        return start.roundToInt()..end.roundToInt()
    }

    private fun fittingItems(
        placeables: List<Placeable>,
        start: Int,
        end: Int,
        overlap: Int,
    ): List<Placeable> {
        var itemStart = start

        return placeables.takeWhile { placeable ->
            val fits = itemStart + placeable.width <= end

            itemStart += placeable.width - overlap
            fits
        }
    }
}
