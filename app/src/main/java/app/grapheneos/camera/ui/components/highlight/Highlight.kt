package app.grapheneos.camera.ui.components.highlight

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer

internal fun DrawScope.drawHighlight(
    start: Float,
    width: Float,
    color: Color,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(
            x = start,
            y = 0f,
        ),
        size = Size(
            width = width,
            height = size.height,
        ),
        cornerRadius = CornerRadius(size.height / 2),
        blendMode = blendMode,
    )
}

/**
 * Draws the part of the highlight that covers this item, then the content, recolored to
 * [highlightContentColor] where the highlight is. Press feedback below this modifier, such as a
 * ripple, stays under the highlight instead of showing through it.
 */
internal fun Modifier.highlightedContent(
    highlightColor: Color,
    highlightContentColor: Color,
    highlightStart: DrawScope.() -> Float,
    highlightWidth: DrawScope.() -> Float,
): Modifier {
    return this
        .drawBehind {
            clipRect {
                drawHighlight(
                    start = highlightStart(),
                    width = highlightWidth(),
                    color = highlightColor,
                )
            }
        }
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawHighlight(
                start = highlightStart(),
                width = highlightWidth(),
                color = highlightContentColor,
                blendMode = BlendMode.SrcAtop,
            )
        }
}
