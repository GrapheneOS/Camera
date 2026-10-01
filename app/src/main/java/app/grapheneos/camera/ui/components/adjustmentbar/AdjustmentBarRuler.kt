package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal fun DrawScope.drawAdjustmentBar(
    position: Float,
    scale: AdjustmentBarScale,
    majorTickInterval: Int,
    hasStartIcon: Boolean,
    hasEndIcon: Boolean,
    isFocused: Boolean,
    metrics: AdjustmentBarMetrics,
    colors: AdjustmentBarColors,
) {
    drawRoundRect(
        color = colors.containerColor,
        cornerRadius = CornerRadius(size.height / 2),
    )
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
            majorTickInterval = majorTickInterval,
            left = inset(hasIcon = hasStartIcon),
            right = size.width - inset(hasIcon = hasEndIcon),
            metrics = metrics,
            colors = colors,
        )
    }
    drawRoundRect(
        color = colors.indicatorColor,
        topLeft = center - metrics.indicatorSize.center,
        size = metrics.indicatorSize,
        cornerRadius = CornerRadius(metrics.indicatorSize.width / 2),
    )
    if (isFocused) {
        drawFocusRing(
            stroke = metrics.focusStroke,
            colors = colors,
        )
    }
}

private fun DrawScope.drawTicks(
    position: Float,
    scale: AdjustmentBarScale,
    majorTickInterval: Int,
    left: Float,
    right: Float,
    metrics: AdjustmentBarMetrics,
    colors: AdjustmentBarColors,
) {
    val ticksToLeft = (center.x - left) / metrics.tickSpacing
    val ticksToRight = (right - center.x) / metrics.tickSpacing
    val firstTick = max(0, ceil(position - ticksToLeft).toInt())
    val lastTick = min(scale.lastTick, floor(position + ticksToRight).toInt())
    val capInset = metrics.tickWidth / 2

    for (tick in firstTick..lastTick) {
        val x = center.x + (tick - position) * metrics.tickSpacing
        val isMajor = tick % majorTickInterval == 0
        val height = when {
            isMajor -> metrics.majorTickHeight
            else -> metrics.minorTickHeight
        }

        drawLine(
            color = when {
                isMajor -> colors.majorTickColor
                else -> colors.tickColor
            },
            start = Offset(x = x, y = center.y - height / 2 + capInset),
            end = Offset(x = x, y = center.y + height / 2 - capInset),
            strokeWidth = metrics.tickWidth,
            cap = StrokeCap.Round,
            alpha = (min(x - left, right - x) / metrics.fadeWidth).coerceIn(0f, 1f),
        )
    }
}

private fun DrawScope.inset(hasIcon: Boolean): Float {
    return when {
        hasIcon -> size.height
        else -> size.height / 2
    }
}

private fun DrawScope.drawFocusRing(
    stroke: Stroke,
    colors: AdjustmentBarColors,
) {
    val halfWidth = stroke.width / 2

    drawRoundRect(
        color = colors.focusColor,
        topLeft = Offset(x = halfWidth, y = halfWidth),
        size = Size(
            width = size.width - stroke.width,
            height = size.height - stroke.width,
        ),
        cornerRadius = CornerRadius((size.height - stroke.width) / 2),
        style = stroke,
    )
}
