package app.grapheneos.camera.ui.components.countdowntimer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.cameraColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val FONT_SIZE = 190.dp

/** The digits ignore the font scale and shrink to fit the incoming constraints. */
@Composable
internal fun CountDownTimer(
    value: Int,
    modifier: Modifier = Modifier,
    colors: CountDownTimerColors = CountDownTimerColors.fromTheme(),
) {
    require(value >= 0) {
        "value must not be negative, was $value"
    }

    val boldness = rememberCountDownTimerBoldness(value = value)

    CountDownTimerText(
        text = rememberCountDownTimerText(value = value),
        boldness = { boldness.value },
        modifier = modifier,
        colors = colors,
    )
}

@Composable
private fun CountDownTimerText(
    text: String,
    boldness: () -> Float,
    modifier: Modifier = Modifier,
    colors: CountDownTimerColors = CountDownTimerColors.fromTheme(),
) {
    val density = LocalDensity.current
    val emSize = with(density) { FONT_SIZE.toPx() }
    val layout = rememberCountDownTimerLayout(
        text = text,
        fontSize = with(density) { FONT_SIZE.toSp() },
    )
    val strokes = remember(emSize) { CountDownTimerStrokes(emSize = emSize) }
    val inkBounds = remember(layout) {
        layout.getPathForRange(start = 0, end = text.length).getBounds()
    }
    val naturalSize = remember(layout, inkBounds, emSize) {
        val maxStrokeWidth = CountDownTimerStrokes.width(boldness = MAX_BOLDNESS) * emSize

        Size(
            width = max(layout.size.width.toFloat(), inkBounds.width) + maxStrokeWidth,
            height = max(layout.size.height.toFloat(), inkBounds.height + maxStrokeWidth),
        )
    }
    val color = colors.contentColor

    Canvas(
        modifier = modifier
            .semantics {
                this.text = AnnotatedString(text = text)
                liveRegion = LiveRegionMode.Polite
            }
            .fitInto(naturalSize = naturalSize)
            .graphicsLayer {
                alpha = color.alpha
                compositingStrategy = when {
                    color.alpha < 1f -> CompositingStrategy.Offscreen
                    else -> CompositingStrategy.Auto
                }
            },
    ) {
        scale(scale = fitScale(available = size, natural = naturalSize)) {
            drawDigits(
                layout = layout,
                topLeft = center - inkBounds.center,
                color = color.copy(alpha = 1f),
                stroke = strokes.stroke(boldness = boldness()),
            )
        }
    }
}

private fun DrawScope.drawDigits(
    layout: TextLayoutResult,
    topLeft: Offset,
    color: Color,
    stroke: Stroke,
) {
    // Without an explicit Fill the paragraph's paint keeps the stroke of the previous frame.
    drawText(
        textLayoutResult = layout,
        color = color,
        topLeft = topLeft,
        drawStyle = Fill,
    )
    drawText(
        textLayoutResult = layout,
        color = color,
        topLeft = topLeft,
        drawStyle = stroke,
    )
}

private fun Modifier.fitInto(naturalSize: Size): Modifier {
    return layout { measurable, constraints ->
        val scale = fitScale(
            available = Size(
                width = constraints.maxWidth.toFloat(),
                height = constraints.maxHeight.toFloat(),
            ),
            natural = naturalSize,
        )
        val width = (naturalSize.width * scale).roundToInt()
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (naturalSize.height * scale).roundToInt()
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(
            Constraints.fixed(
                width = width,
                height = height,
            ),
        )

        layout(width, height) {
            placeable.place(x = 0, y = 0)
        }
    }
}

private fun fitScale(
    available: Size,
    natural: Size,
): Float {
    return min(
        available.width / natural.width,
        available.height / natural.height,
    ).coerceAtMost(1f)
}

@PreviewLightDark
@Composable
private fun CountDownTimerPreview() {
    CameraPreviewColumn {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewCountDownTimerText(
                text = "3",
                boldness = 1f,
            )
            PreviewCountDownTimerText(
                text = "3",
                boldness = 0f,
            )
            PreviewCountDownTimerText(
                text = "10",
                boldness = 1f,
            )
            PreviewCountDownTimerText(
                text = "10",
                boldness = 0f,
                modifier = Modifier.size(size = 96.dp),
            )
        }
    }
}

@Preview(name = "Arabic", locale = "ar")
@Preview(name = "Persian", locale = "fa")
@Preview(name = "Marathi", locale = "mr")
@Composable
private fun CountDownTimerNumeralsPreview() {
    CameraPreviewColumn(darkTheme = true) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewCountDownTimerText(
                text = rememberCountDownTimerText(value = 3),
                boldness = 1f,
            )
            PreviewCountDownTimerText(
                text = rememberCountDownTimerText(value = 10),
                boldness = 0f,
            )
        }
    }
}

@Composable
private fun PreviewCountDownTimerText(
    text: String,
    boldness: Float,
    modifier: Modifier = Modifier,
) {
    CountDownTimerText(
        text = text,
        boldness = { boldness },
        modifier = modifier.background(
            color = MaterialTheme.cameraColors.overlayScrim,
            shape = MaterialTheme.shapes.large,
        ),
    )
}
