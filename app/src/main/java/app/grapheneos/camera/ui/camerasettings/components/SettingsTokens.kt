package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal val SCREEN_CONTENT_PADDING = 16.dp
internal val GROUPED_ITEM_SPACING = 2.dp

internal val ITEM_MIN_HEIGHT = 80.dp
internal val ITEM_START_PADDING = 24.dp
internal val ITEM_END_PADDING = 16.dp
internal val ITEM_VERTICAL_PADDING = 20.dp
internal val ITEM_ICON_SPACING = 24.dp

@Composable
@ReadOnlyComposable
internal fun groupedItemShape(
    index: Int,
    count: Int,
): Shape {
    val outer = MaterialTheme.shapes.small
    val inner = ShapeDefaults.ExtraSmall

    return when {
        count == 1 -> outer

        index == 0 -> outer.copy(
            bottomStart = inner.bottomStart,
            bottomEnd = inner.bottomEnd,
        )

        index == count - 1 -> outer.copy(
            topStart = inner.topStart,
            topEnd = inner.topEnd,
        )

        else -> inner
    }
}
