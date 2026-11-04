package app.grapheneos.camera.ui.statusbar.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val EDGE_PADDING = 8.dp
private val SETTINGS_END_PADDING = 24.dp
private val BOTTOM_PADDING = 4.dp
private val ITEM_OVERLAP = 8.dp

// Items go from the start up to a cutout in the middle of the bar, and stop at the first one that
// does not fit, so an item never takes the place of one before it. The settings button stays at
// the end, moved before a cutout in the end corner.
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
        val settingsEnd = width - SETTINGS_END_PADDING.roundToPx()
        val cutoutSpan = cutoutSpan(
            width = width,
            rowTop = rowTop,
        )
        val settingsStart = settingsStart(
            cutoutSpan = cutoutSpan,
            settingsEnd = settingsEnd,
            settingsWidth = settingsPlaceable.width,
        )
        val itemsStart = when {
            cutoutSpan != null && cutoutSpan.first <= edgePadding -> cutoutSpan.last
            else -> edgePadding
        }
        val itemsEnd = when {
            cutoutSpan != null && cutoutSpan.first > itemsStart -> {
                minOf(cutoutSpan.first, settingsStart)
            }

            else -> settingsStart
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

    private fun settingsStart(
        cutoutSpan: IntRange?,
        settingsEnd: Int,
        settingsWidth: Int,
    ): Int {
        val coversSettings = cutoutSpan != null &&
            cutoutSpan.last > settingsEnd - settingsWidth &&
            cutoutSpan.first < settingsEnd

        return when {
            coversSettings -> cutoutSpan.first - settingsWidth
            else -> settingsEnd - settingsWidth
        }
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
