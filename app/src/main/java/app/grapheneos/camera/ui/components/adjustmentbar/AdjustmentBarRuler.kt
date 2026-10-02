package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.ui.graphics.drawscope.DrawScope
import app.grapheneos.camera.ui.components.ruler.RulerMetrics
import app.grapheneos.camera.ui.components.ruler.RulerTickAlignment
import app.grapheneos.camera.ui.components.ruler.RulerWindow
import app.grapheneos.camera.ui.components.ruler.drawRulerContainer
import app.grapheneos.camera.ui.components.ruler.drawRulerFocusRing
import app.grapheneos.camera.ui.components.ruler.drawRulerIndicator
import app.grapheneos.camera.ui.components.ruler.drawRulerTicks

internal fun DrawScope.drawAdjustmentBar(
    position: Float,
    scale: AdjustmentBarScale,
    window: RulerWindow,
    isFocused: Boolean,
    tint: AdjustmentBarTint,
    metrics: RulerMetrics,
    colors: AdjustmentBarColors,
) {
    drawRulerContainer(color = colors.containerColor)
    tint.draw(
        drawScope = this,
        position = position,
        lastTick = scale.lastTick,
    )
    drawRulerTicks(
        position = position,
        scale = scale,
        window = window,
        metrics = metrics,
        tickColor = colors.tickColor,
        majorTickColor = colors.majorTickColor,
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

internal fun adjustmentBarWindow(
    height: Float,
    hasStartIcon: Boolean,
    hasEndIcon: Boolean,
): RulerWindow {
    return RulerWindow(
        startInset = inset(
            height = height,
            hasIcon = hasStartIcon,
        ),
        endInset = inset(
            height = height,
            hasIcon = hasEndIcon,
        ),
        centerY = height / 2,
        alignment = RulerTickAlignment.Center,
    )
}

private fun inset(
    height: Float,
    hasIcon: Boolean,
): Float {
    return when {
        hasIcon -> height
        else -> height / 2
    }
}
