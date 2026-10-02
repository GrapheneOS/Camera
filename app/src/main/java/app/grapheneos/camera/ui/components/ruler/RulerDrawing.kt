package app.grapheneos.camera.ui.components.ruler

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal fun DrawScope.drawRulerContainer(color: Color) {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(size.height / 2),
    )
}

internal fun DrawScope.drawRulerTicks(
    position: Float,
    scale: RulerScale,
    startInset: Float,
    endInset: Float,
    metrics: RulerMetrics,
    tickColor: Color,
    majorTickColor: Color,
) {
    scale(
        scaleX = when (layoutDirection) {
            LayoutDirection.Rtl -> -1f
            LayoutDirection.Ltr -> 1f
        },
        scaleY = 1f,
    ) {
        drawTicks(
            position = position,
            scale = scale,
            left = startInset,
            right = size.width - endInset,
            metrics = metrics,
            tickColor = tickColor,
            majorTickColor = majorTickColor,
        )
    }
}

internal fun DrawScope.drawRulerIndicator(
    metrics: RulerMetrics,
    color: Color,
) {
    drawRoundRect(
        color = color,
        topLeft = center - metrics.indicatorSize.center,
        size = metrics.indicatorSize,
        cornerRadius = CornerRadius(metrics.indicatorSize.width / 2),
    )
}

internal fun DrawScope.drawRulerFocusRing(
    stroke: Stroke,
    color: Color,
) {
    val halfWidth = stroke.width / 2

    drawRoundRect(
        color = color,
        topLeft = Offset(x = halfWidth, y = halfWidth),
        size = Size(
            width = size.width - stroke.width,
            height = size.height - stroke.width,
        ),
        cornerRadius = CornerRadius((size.height - stroke.width) / 2),
        style = stroke,
    )
}

private fun DrawScope.drawTicks(
    position: Float,
    scale: RulerScale,
    left: Float,
    right: Float,
    metrics: RulerMetrics,
    tickColor: Color,
    majorTickColor: Color,
) {
    val ticksToLeft = (center.x - left) / metrics.tickSpacing
    val ticksToRight = (right - center.x) / metrics.tickSpacing
    val firstTick = max(0, ceil(position - ticksToLeft).toInt())
    val lastTick = min(scale.lastTick, floor(position + ticksToRight).toInt())
    val capInset = metrics.tickWidth / 2

    for (tick in firstTick..lastTick) {
        val x = center.x + (tick - position) * metrics.tickSpacing
        val isMajor = scale.isMajor(tick = tick)
        val height = when {
            isMajor -> metrics.majorTickHeight
            else -> metrics.minorTickHeight
        }

        drawLine(
            color = when {
                isMajor -> majorTickColor
                else -> tickColor
            },
            start = Offset(x = x, y = center.y - height / 2 + capInset),
            end = Offset(x = x, y = center.y + height / 2 - capInset),
            strokeWidth = metrics.tickWidth,
            cap = StrokeCap.Round,
            alpha = (min(x - left, right - x) / metrics.fadeWidth).coerceIn(0f, 1f),
        )
    }
}
