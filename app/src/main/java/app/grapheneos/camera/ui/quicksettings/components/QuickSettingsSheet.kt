package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val TOP_ALPHA = 0.7f

private val CONTENT_FADE = 48.dp
private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp
private val HANDLE_TOP_PADDING = 16.dp
private val HANDLE_BOTTOM_PADDING = 8.dp

/**
 * The tail doesn't take touches. The bottom [solidHeight] of the sheet is opaque to
 * hide the preview's edge, and the content fades out before it reaches the tail.
 */
@Composable
internal fun QuickSettingsSheet(
    state: QuickSettingsSheetState,
    solidHeight: Dp,
    tailHeight: Dp,
    onSettle: (isShown: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = MaterialTheme.colorScheme.surfaceContainer
    val shape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = ZeroCornerSize,
        bottomEnd = ZeroCornerSize,
    )
    val solidHeightPx = with(LocalDensity.current) { solidHeight.toPx() }
    val brush = remember(color, solidHeightPx) {
        SolidBottomGradient(
            topColor = color.copy(alpha = TOP_ALPHA),
            color = color,
            solidHeight = solidHeightPx,
        )
    }

    LaunchedEffect(state) {
        state.animate()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .sheetOffset(state = state),
    ) {
        Column(
            modifier = Modifier
                .weight(weight = 1f, fill = false)
                .clip(shape = shape)
                .background(brush = brush),
        ) {
            SheetBody(
                state = state,
                onSettle = onSettle,
                modifier = Modifier.weight(weight = 1f, fill = false),
                content = content,
            )
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
private fun SheetBody(
    state: QuickSettingsSheetState,
    onSettle: (isShown: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val nestedScrollConnection = rememberSheetNestedScrollConnection(
        state = state,
        onSettle = onSettle,
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .nestedScroll(connection = nestedScrollConnection)
            .draggable(
                state = rememberDraggableState { delta -> state.drag(delta) },
                orientation = Orientation.Vertical,
                onDragStarted = { state.startDrag() },
                onDragStopped = { velocity -> onSettle(state.release(velocity)) },
            ),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fadeAboveTail(state = state),
        ) {
            SheetHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            content()
        }
    }
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

private fun Modifier.sheetOffset(state: QuickSettingsSheetState): Modifier {
    val semantics = when {
        state.isHidden -> Modifier.clearAndSetSemantics {}
        else -> Modifier
    }

    return this
        .then(semantics)
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            state.updateHeight(height = placeable.height.toFloat())

            layout(width = placeable.width, height = placeable.height) {
                placeable.place(x = 0, y = state.offset().roundToInt())
            }
        }
}

private fun Modifier.fadeAboveTail(state: QuickSettingsSheetState): Modifier {
    return this
        .graphicsLayer {
            alpha = state.visibleFraction()
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithContent {
            val offset = state.offset()
            val fadeEnd = size.height - offset

            drawContent()
            if (offset > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = fadeEnd - minOf(CONTENT_FADE.toPx(), offset),
                        endY = fadeEnd,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
