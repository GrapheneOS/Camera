package app.grapheneos.camera.ui.components.modeselector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.modeselector.gesture.ModeSelectorState

private val HIGHLIGHT_PADDING = 20.dp

@Composable
internal fun rememberModeSelectorMeasurePolicy(state: ModeSelectorState): MeasurePolicy {
    return remember(state) {
        MeasurePolicy { measurables, constraints ->
            val items = measurables.map { measurable ->
                measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            }
            val width = when {
                constraints.hasBoundedWidth -> constraints.maxWidth
                else -> items.sumOf { item -> item.width }
            }
            val height = items.maxOfOrNull { item -> item.height } ?: 0
            val geometry = ModeSelectorGeometry(
                itemWidths = items.map { item -> item.width.toFloat() },
                containerWidth = width.toFloat(),
                highlightInset = (ITEM_PADDING - HIGHLIGHT_PADDING).toPx(),
            )

            state.geometry = geometry
            layout(width, height) {
                items.forEachIndexed { index, item ->
                    item.placeRelative(
                        x = geometry.itemStart(
                            index = index,
                            position = state.position,
                        ),
                        y = (height - item.height) / 2,
                    )
                }
            }
        }
    }
}
