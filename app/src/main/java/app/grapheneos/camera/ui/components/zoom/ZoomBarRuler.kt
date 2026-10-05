package app.grapheneos.camera.ui.components.zoom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.grapheneos.camera.ui.components.ruler.RulerMetrics
import app.grapheneos.camera.ui.components.ruler.RulerTickAlignment
import app.grapheneos.camera.ui.components.ruler.RulerWindow
import app.grapheneos.camera.ui.components.ruler.drawRulerContainer
import app.grapheneos.camera.ui.components.ruler.drawRulerFocusRing
import app.grapheneos.camera.ui.components.ruler.drawRulerIndicator
import app.grapheneos.camera.ui.components.ruler.drawRulerTicks
import app.grapheneos.camera.ui.components.ruler.rulerMarkAlpha
import app.grapheneos.camera.ui.components.ruler.rulerMarkX
import kotlin.math.abs

private const val SELECTED_LABEL_SCALE = 1.1f
private const val LABEL_GROWTH_TICKS = 1f

private val TICKS_RAISE = 6.dp
private val LABEL_OFFSET = 12.dp

internal fun DrawScope.drawZoomBar(
    position: Float,
    scale: ZoomBarScale,
    labels: List<TextLayoutResult>,
    labelRotation: Float,
    window: RulerWindow,
    isFocused: Boolean,
    metrics: RulerMetrics,
    colors: ZoomBarColors,
) {
    drawRulerContainer(color = colors.containerColor)
    drawRulerTicks(
        position = position,
        scale = scale,
        window = window,
        metrics = metrics,
        tickColor = colors.tickColor,
        majorTickColor = colors.stopColor,
    )
    drawLabels(
        position = position,
        scale = scale,
        labels = labels,
        labelRotation = labelRotation,
        window = window,
        metrics = metrics,
        colors = colors,
    )
    drawRulerIndicator(
        centerY = window.centerY,
        metrics = metrics,
        color = colors.indicatorColor,
    )
    if (isFocused) {
        drawRulerFocusRing(
            stroke = metrics.focusStroke,
            color = colors.focusColor,
        )
    }
}

internal fun Density.zoomBarWindow(height: Float): RulerWindow {
    return RulerWindow(
        startInset = height / 2,
        endInset = height / 2,
        centerY = height / 2 - TICKS_RAISE.toPx(),
        alignment = RulerTickAlignment.Bottom,
    )
}

@Composable
internal fun rememberZoomBarLabels(
    marks: List<Float>,
    format: ZoomFormat,
    style: TextStyle,
): List<TextLayoutResult> {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    return remember(marks, format, style, textMeasurer, density) {
        marks.map { mark ->
            textMeasurer.measure(
                text = format.format(value = mark),
                style = style,
            )
        }
    }
}

private fun DrawScope.drawLabels(
    position: Float,
    scale: ZoomBarScale,
    labels: List<TextLayoutResult>,
    labelRotation: Float,
    window: RulerWindow,
    metrics: RulerMetrics,
    colors: ZoomBarColors,
) {
    val labelCenterY = size.height / 2 + LABEL_OFFSET.toPx()

    for (index in labels.indices) {
        val stopPosition = scale.markTicks[index].toFloat()
        val label = labels[index]
        val alpha = rulerMarkAlpha(
            markPosition = stopPosition,
            position = position,
            window = window,
            metrics = metrics,
        )

        if (alpha > 0f) {
            val labelCenter = Offset(
                x = rulerMarkX(
                    markPosition = stopPosition,
                    position = position,
                    metrics = metrics,
                ),
                y = labelCenterY,
            )
            val distance = abs(stopPosition - position)
            val selection = (1f - distance / LABEL_GROWTH_TICKS).coerceIn(0f, 1f)
            val labelScale = lerp(
                start = 1f,
                stop = SELECTED_LABEL_SCALE,
                fraction = selection,
            )

            withTransform(
                transformBlock = {
                    scale(
                        scaleX = labelScale,
                        scaleY = labelScale,
                        pivot = labelCenter,
                    )
                    rotate(
                        degrees = labelRotation,
                        pivot = labelCenter,
                    )
                },
            ) {
                drawText(
                    textLayoutResult = label,
                    color = colors.stopColor,
                    topLeft = Offset(
                        x = labelCenter.x - label.size.width / 2f,
                        y = labelCenter.y - label.size.height / 2f,
                    ),
                    alpha = alpha,
                )
            }
        }
    }
}
