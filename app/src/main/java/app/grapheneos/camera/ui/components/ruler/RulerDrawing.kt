package app.grapheneos.camera.ui.components.ruler

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
    window: RulerWindow,
    metrics: RulerMetrics,
    tickColor: Color,
    majorTickColor: Color,
) {
    val ticksBefore = (center.x - window.startInset) / metrics.tickSpacing
    val ticksAfter = (size.width - window.endInset - center.x) / metrics.tickSpacing
    val firstTick = max(0, ceil(position - ticksBefore).toInt())
    val lastTick = min(scale.lastTick, floor(position + ticksAfter).toInt())

    for (tick in firstTick..lastTick) {
        val isMajor = scale.isMajor(tick = tick)

        drawTick(
            markPosition = tick.toFloat(),
            position = position,
            height = when {
                isMajor -> metrics.majorTickHeight
                else -> metrics.minorTickHeight
            },
            color = when {
                isMajor -> majorTickColor
                else -> tickColor
            },
            window = window,
            metrics = metrics,
        )
    }
}

internal fun DrawScope.drawRulerIndicator(
    centerY: Float,
    metrics: RulerMetrics,
    color: Color,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(
            x = center.x - metrics.indicatorSize.width / 2,
            y = centerY - metrics.indicatorSize.height / 2,
        ),
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

internal fun DrawScope.rulerMarkX(
    markPosition: Float,
    position: Float,
    metrics: RulerMetrics,
): Float {
    val offset = (markPosition - position) * metrics.tickSpacing

    return when (layoutDirection) {
        LayoutDirection.Ltr -> center.x + offset
        LayoutDirection.Rtl -> center.x - offset
    }
}

internal fun DrawScope.rulerMarkAlpha(
    markPosition: Float,
    position: Float,
    window: RulerWindow,
    metrics: RulerMetrics,
): Float {
    val x = center.x + (markPosition - position) * metrics.tickSpacing
    val distanceToEdge = min(x - window.startInset, size.width - window.endInset - x)

    return (distanceToEdge / metrics.fadeWidth).coerceIn(0f, 1f)
}

private fun DrawScope.drawTick(
    markPosition: Float,
    position: Float,
    height: Float,
    color: Color,
    window: RulerWindow,
    metrics: RulerMetrics,
) {
    val x = rulerMarkX(
        markPosition = markPosition,
        position = position,
        metrics = metrics,
    )
    val capInset = metrics.tickWidth / 2
    val bottom = when (window.alignment) {
        RulerTickAlignment.Center -> window.centerY + height / 2
        RulerTickAlignment.Bottom -> window.centerY + metrics.indicatorSize.height / 2
    }

    drawLine(
        color = color,
        start = Offset(x = x, y = bottom - height + capInset),
        end = Offset(x = x, y = bottom - capInset),
        strokeWidth = metrics.tickWidth,
        cap = StrokeCap.Round,
        alpha = rulerMarkAlpha(
            markPosition = markPosition,
            position = position,
            window = window,
            metrics = metrics,
        ),
    )
}
