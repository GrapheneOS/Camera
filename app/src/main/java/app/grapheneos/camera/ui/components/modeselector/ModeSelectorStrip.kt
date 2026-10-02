package app.grapheneos.camera.ui.components.modeselector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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

internal fun DrawScope.drawModeSelectorHighlight(
    state: ModeSelectorState,
    color: Color,
) {
    val width = state.geometry.highlightWidth(position = state.position)

    drawRoundRect(
        color = color,
        topLeft = Offset(
            x = (size.width - width) / 2,
            y = 0f,
        ),
        size = Size(
            width = width,
            height = size.height,
        ),
        cornerRadius = CornerRadius(size.height / 2),
    )
}
