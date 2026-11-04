package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
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
private val CROSSHAIR_ARM = 10.dp
private val CROSSHAIR_DOT_RADIUS = 2.25.dp
private val CROSSHAIR_SHIFT_RANGE = 24.dp
private val HORIZON_SHIFT_RANGE = 40.dp

private const val CROSSHAIR_TILT_RANGE = 15f
private const val HORIZON_PITCH_RANGE = 45f

internal fun DrawScope.drawLevelIndicator(
    motion: LevelIndicatorMotion,
    labels: LevelIndicatorLabels,
    paint: LevelPaint,
    colors: LevelIndicatorColors,
) {
    when (motion.mode) {
        LevelIndicatorMode.Horizon -> drawHorizon(
            motion = motion,
            labels = labels,
            paint = paint,
            colors = colors,
        )

        LevelIndicatorMode.Crosshairs -> drawCrosshairs(
            motion = motion,
            paint = paint,
            colors = colors,
        )
    }
}

private fun DrawScope.drawHorizon(
    motion: LevelIndicatorMotion,
    labels: LevelIndicatorLabels,
    paint: LevelPaint,
    colors: LevelIndicatorColors,
) {
    val color = lerp(
        start = colors.lineColor,
        stop = colors.levelColor,
        fraction = motion.pitchAlignmentFraction,
    )

    drawDashes(
        paint = paint,
        color = color,
    )
    rotate(degrees = motion.roll) {
        drawHorizonLine(
            paint = paint,
            halfLength = lerp(
                start = LINE_LENGTH.toPx(),
                stop = LEVEL_LINE_LENGTH.toPx(),
                fraction = motion.rollAlignmentFraction,
            ) / 2,
            y = center.y,
            alignmentFraction = motion.rollAlignmentFraction,
            color = color,
        )
        drawHorizonLine(
            paint = paint,
            halfLength = LINE_LENGTH.toPx() / 2,
            y = center.y + horizonShift(degrees = motion.pitch),
            alignmentFraction = 0f,
            color = colors.levelColor,
            alpha = 1f - motion.rollAlignmentFraction * motion.pitchAlignmentFraction,
        )
        drawCenteredText(
            text = labels.label(degrees = abs(motion.roll).roundToInt()),
            center = center - Offset(x = 0f, y = LABEL_OFFSET.toPx()),
            color = color,
            shadow = labels.shadow,
        )
    }
}

private fun DrawScope.drawCrosshairs(
    motion: LevelIndicatorMotion,
    paint: LevelPaint,
    colors: LevelIndicatorColors,
) {
    val tilt = LevelZone.tiltFromVertical(pitch = motion.pitch)
    val shift = crosshairShift(degrees = tilt) * sign(motion.pitch)
    val radians = Math.toRadians(motion.roll.toDouble())

    drawCrosshair(
        paint = paint,
        center = center,
        alignmentFraction = motion.verticalAlignmentFraction,
        color = lerp(
            start = colors.lineColor,
            stop = colors.levelColor,
            fraction = motion.verticalAlignmentFraction,
        ),
    )
    drawCrosshair(
        paint = paint,
        center = center + Offset(
            x = shift * sin(radians).toFloat(),
            y = -shift * cos(radians).toFloat(),
        ),
        alignmentFraction = motion.verticalAlignmentFraction,
        color = colors.levelColor,
    )
}

private fun DrawScope.drawDashes(
    paint: LevelPaint,
    color: Color,
) {
    val outer = LEVEL_LINE_LENGTH.toPx() / 2
    val inner = outer - DASH_LENGTH.toPx()

    drawIntoCanvas { canvas ->
        paint.drawLine(
            canvas = canvas,
            start = center - Offset(x = outer, y = 0f),
            end = center - Offset(x = inner, y = 0f),
            width = lineWidth(alignmentFraction = 0f),
            color = color,
        )
        paint.drawLine(
            canvas = canvas,
            start = center + Offset(x = inner, y = 0f),
            end = center + Offset(x = outer, y = 0f),
            width = lineWidth(alignmentFraction = 0f),
            color = color,
        )
    }
}

private fun DrawScope.drawHorizonLine(
    paint: LevelPaint,
    halfLength: Float,
    y: Float,
    alignmentFraction: Float,
    color: Color,
    alpha: Float = 1f,
) {
    drawIntoCanvas { canvas ->
        paint.drawLine(
            canvas = canvas,
            start = Offset(x = center.x - halfLength, y = y),
            end = Offset(x = center.x + halfLength, y = y),
            width = lineWidth(alignmentFraction = alignmentFraction),
            color = color,
            alpha = alpha,
        )
    }
}

private fun DrawScope.drawCrosshair(
    paint: LevelPaint,
    center: Offset,
    alignmentFraction: Float,
    color: Color,
) {
    val arm = CROSSHAIR_ARM.toPx()

    drawIntoCanvas { canvas ->
        paint.drawLine(
            canvas = canvas,
            start = center - Offset(x = arm, y = 0f),
            end = center + Offset(x = arm, y = 0f),
            width = lineWidth(alignmentFraction = alignmentFraction),
            color = color,
        )
        paint.drawLine(
            canvas = canvas,
            start = center - Offset(x = 0f, y = arm),
            end = center + Offset(x = 0f, y = arm),
            width = lineWidth(alignmentFraction = alignmentFraction),
            color = color,
        )
        paint.drawDot(
            canvas = canvas,
            center = center,
            radius = CROSSHAIR_DOT_RADIUS.toPx(),
            color = color,
        )
    }
}

private fun DrawScope.lineWidth(alignmentFraction: Float): Float {
    return lerp(
        start = LINE_WIDTH.toPx(),
        stop = LEVEL_LINE_WIDTH.toPx(),
        fraction = alignmentFraction,
    )
}

private fun DrawScope.horizonShift(degrees: Float): Float {
    val fraction = degrees.coerceIn(-HORIZON_PITCH_RANGE, HORIZON_PITCH_RANGE) / HORIZON_PITCH_RANGE
    return fraction * HORIZON_SHIFT_RANGE.toPx()
}

private fun DrawScope.crosshairShift(degrees: Float): Float {
    val fraction = degrees.coerceIn(0f, CROSSHAIR_TILT_RANGE) / CROSSHAIR_TILT_RANGE
    return fraction * CROSSHAIR_SHIFT_RANGE.toPx()
}
