package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.grapheneos.camera.ui.components.motion.shortestTurn
import app.grapheneos.camera.ui.components.text.drawCenteredText
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

internal val LEVEL_LINE_LENGTH = 124.dp

private val LINE_LENGTH = 116.dp
private val LINE_WIDTH = 1.5.dp
private val LEVEL_LINE_WIDTH = 2.5.dp
private val DASH_LENGTH = 4.dp
private val LABEL_OFFSET = 10.dp
private val CROSS_ARM = 10.dp
private val CROSS_DOT_RADIUS = 2.25.dp
private val TILT_SHIFT_RANGE = 24.dp

private const val TILT_RANGE = 45f

internal fun DrawScope.drawLevelIndicator(
    motion: LevelIndicatorMotion,
    labels: LevelIndicatorLabels,
    paint: LevelPaint,
    colors: LevelIndicatorColors,
) {
    drawHorizonLevel(
        motion = motion,
        labels = labels,
        paint = paint,
        colors = colors,
        alpha = 1f - motion.topDown,
    )
    drawTopDownLevel(
        motion = motion,
        paint = paint,
        colors = colors,
        alpha = motion.topDown,
    )
}

private fun DrawScope.drawHorizonLevel(
    motion: LevelIndicatorMotion,
    labels: LevelIndicatorLabels,
    paint: LevelPaint,
    colors: LevelIndicatorColors,
    alpha: Float,
) {
    if (alpha <= 0f) return

    val color = lerp(
        start = colors.lineColor,
        stop = colors.levelColor,
        fraction = motion.pitchLevel,
    )

    drawDashes(
        paint = paint,
        color = color,
        alpha = alpha,
    )
    rotate(degrees = motion.roll) {
        val rollDegrees = shortestTurn(
            from = 0f,
            to = motion.roll,
        )

        drawLevelLine(
            paint = paint,
            halfLength = lerp(
                start = LINE_LENGTH.toPx(),
                stop = LEVEL_LINE_LENGTH.toPx(),
                fraction = motion.rollLevel,
            ) / 2,
            y = center.y,
            level = motion.rollLevel,
            color = color,
            alpha = alpha,
        )
        drawLevelLine(
            paint = paint,
            halfLength = LINE_LENGTH.toPx() / 2,
            y = center.y + tiltShift(degrees = motion.pitch),
            level = 0f,
            color = colors.levelColor,
            alpha = alpha * (1f - motion.rollLevel * motion.pitchLevel),
        )
        drawCenteredText(
            text = labels.label(degrees = abs(rollDegrees).roundToInt()),
            center = center - Offset(x = 0f, y = LABEL_OFFSET.toPx()),
            color = color,
            alpha = alpha,
            shadow = labels.shadow(alpha = alpha),
        )
    }
}

private fun DrawScope.drawTopDownLevel(
    motion: LevelIndicatorMotion,
    paint: LevelPaint,
    colors: LevelIndicatorColors,
    alpha: Float,
) {
    if (alpha <= 0f) return

    val tilt = LevelZone.tiltFromVertical(pitch = motion.pitch)
    val shift = tiltShift(degrees = tilt) * sign(motion.pitch)
    val radians = Math.toRadians(motion.roll.toDouble())

    drawCross(
        paint = paint,
        center = center,
        level = motion.verticalLevel,
        color = lerp(
            start = colors.lineColor,
            stop = colors.levelColor,
            fraction = motion.verticalLevel,
        ),
        alpha = alpha,
    )
    drawCross(
        paint = paint,
        center = center + Offset(
            x = shift * sin(radians).toFloat(),
            y = -shift * cos(radians).toFloat(),
        ),
        level = motion.verticalLevel,
        color = colors.levelColor,
        alpha = alpha,
    )
}

private fun DrawScope.drawDashes(
    paint: LevelPaint,
    color: Color,
    alpha: Float,
) {
    val outer = LEVEL_LINE_LENGTH.toPx() / 2
    val inner = outer - DASH_LENGTH.toPx()

    drawIntoCanvas { canvas ->
        paint.drawLine(
            canvas = canvas,
            start = center - Offset(x = outer, y = 0f),
            end = center - Offset(x = inner, y = 0f),
            width = lineWidth(level = 0f),
            color = color,
            alpha = alpha,
        )
        paint.drawLine(
            canvas = canvas,
            start = center + Offset(x = inner, y = 0f),
            end = center + Offset(x = outer, y = 0f),
            width = lineWidth(level = 0f),
            color = color,
            alpha = alpha,
        )
    }
}

private fun DrawScope.drawLevelLine(
    paint: LevelPaint,
    halfLength: Float,
    y: Float,
    level: Float,
    color: Color,
    alpha: Float,
) {
    drawIntoCanvas { canvas ->
        paint.drawLine(
            canvas = canvas,
            start = Offset(x = center.x - halfLength, y = y),
            end = Offset(x = center.x + halfLength, y = y),
            width = lineWidth(level = level),
            color = color,
            alpha = alpha,
        )
    }
}

private fun DrawScope.drawCross(
    paint: LevelPaint,
    center: Offset,
    level: Float,
    color: Color,
    alpha: Float,
) {
    val arm = CROSS_ARM.toPx()

    drawIntoCanvas { canvas ->
        paint.drawLine(
            canvas = canvas,
            start = center - Offset(x = arm, y = 0f),
            end = center + Offset(x = arm, y = 0f),
            width = lineWidth(level = level),
            color = color,
            alpha = alpha,
        )
        paint.drawLine(
            canvas = canvas,
            start = center - Offset(x = 0f, y = arm),
            end = center + Offset(x = 0f, y = arm),
            width = lineWidth(level = level),
            color = color,
            alpha = alpha,
        )
        paint.drawDot(
            canvas = canvas,
            center = center,
            radius = CROSS_DOT_RADIUS.toPx(),
            color = color,
            alpha = alpha,
        )
    }
}

private fun DrawScope.lineWidth(level: Float): Float {
    return lerp(
        start = LINE_WIDTH.toPx(),
        stop = LEVEL_LINE_WIDTH.toPx(),
        fraction = level,
    )
}

private fun DrawScope.tiltShift(degrees: Float): Float {
    return degrees.coerceIn(-TILT_RANGE, TILT_RANGE) / TILT_RANGE * TILT_SHIFT_RANGE.toPx()
}
