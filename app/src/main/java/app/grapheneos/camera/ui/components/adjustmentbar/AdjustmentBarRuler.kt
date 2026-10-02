package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.ui.graphics.drawscope.DrawScope
import app.grapheneos.camera.ui.components.ruler.RulerMetrics
import app.grapheneos.camera.ui.components.ruler.drawRulerContainer
import app.grapheneos.camera.ui.components.ruler.drawRulerFocusRing
import app.grapheneos.camera.ui.components.ruler.drawRulerIndicator
import app.grapheneos.camera.ui.components.ruler.drawRulerTicks

internal fun DrawScope.drawAdjustmentBar(
    position: Float,
    scale: AdjustmentBarScale,
    hasStartIcon: Boolean,
    hasEndIcon: Boolean,
    isFocused: Boolean,
    tint: AdjustmentBarTint,
    metrics: RulerMetrics,
    colors: AdjustmentBarColors,
) {
    drawRulerContainer(
        color = colors.containerColor,
    )
    tint.draw(
        drawScope = this,
        position = position,
        lastTick = scale.lastTick,
    )
    drawRulerTicks(
        position = position,
        scale = scale,
        startInset = inset(hasIcon = hasStartIcon),
        endInset = inset(hasIcon = hasEndIcon),
        metrics = metrics,
        tickColor = colors.tickColor,
        majorTickColor = colors.majorTickColor,
    )
    drawRulerIndicator(
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

private fun DrawScope.inset(hasIcon: Boolean): Float {
    return when {
        hasIcon -> size.height
        else -> size.height / 2
    }
}
