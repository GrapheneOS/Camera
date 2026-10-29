package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.shape.ConcaveTopCornersShape
import kotlin.math.roundToInt

private const val TOP_ALPHA = 0.7f

private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp
private val HANDLE_TOP_PADDING = 16.dp
private val HANDLE_BOTTOM_PADDING = 8.dp

/**
 * Touches on the corners and the tail pass through. [solidHeight] above the tail is opaque, so
 * that the preview's edge behind it stays hidden.
 */
@Composable
internal fun QuickSettingsSheet(
    solidHeight: Dp,
    tailHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = MaterialTheme.colorScheme.surfaceContainer
    val cornerSize = MaterialTheme.shapes.extraLarge.topStart
    val shape = remember(cornerSize) { ConcaveTopCornersShape(cornerSize = cornerSize) }
    val brush = SolidBottomGradient(
        topColor = color.copy(alpha = TOP_ALPHA),
        color = color,
        solidHeight = with(LocalDensity.current) { solidHeight.toPx() },
    )

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .weight(weight = 1f, fill = false)
                .background(
                    brush = brush,
                    shape = shape,
                ),
        ) {
            CornersSpace(shape = shape)
            Surface(
                modifier = Modifier
                    .weight(weight = 1f, fill = false)
                    .fillMaxWidth(),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column {
                    SheetHandle(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    content()
                }
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(height = tailHeight)
                .background(color = color),
        )
    }
}

@Composable
private fun CornersSpace(shape: ConcaveTopCornersShape) {
    Spacer(
        modifier = Modifier.layout { measurable, constraints ->
            val width = constraints.maxWidth
            val height = shape.radius(width = width.toFloat(), density = this).roundToInt()
            val placeable = measurable.measure(Constraints.fixed(width = width, height = height))

            layout(width = width, height = height) {
                placeable.place(x = 0, y = 0)
            }
        },
    )
}

@Composable
private fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(
                top = HANDLE_TOP_PADDING,
                bottom = HANDLE_BOTTOM_PADDING,
            )
            .size(
                width = HANDLE_WIDTH,
                height = HANDLE_HEIGHT,
            )
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.extraSmall,
            ),
    )
}
