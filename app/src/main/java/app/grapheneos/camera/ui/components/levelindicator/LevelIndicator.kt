package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.components.text.drawCenteredText
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.CameraPreviewViewfinder
import kotlin.math.abs
import kotlin.math.roundToInt

private val LINE_LENGTH = 116.dp
private val LEVEL_LINE_LENGTH = 124.dp
private val LINE_WIDTH = 1.5.dp
private val LEVEL_LINE_WIDTH = 2.5.dp
private val SHADOW_RADIUS = 1.dp
private val DASH_LENGTH = 4.dp
private val LABEL_OFFSET = 10.dp
private val HORIZON_SHIFT_RANGE = 24.dp

private const val PITCH_RANGE = 45f

/**
 * Draws around its center, so the caller centers it on the image. A level roll makes the roll line
 * bolder and reach the dashes; a level pitch puts the horizon line under it, and everything turns
 * the level color.
 *
 * @param roll how far the horizon is turned clockwise, in degrees.
 * @param pitch how far the camera points above the horizon, in degrees; it moves the horizon line
 * down.
 */
@Composable
internal fun LevelIndicator(
    roll: () -> Float,
    pitch: () -> Float,
    modifier: Modifier = Modifier,
    colors: LevelIndicatorColors = LevelIndicatorColors.fromTheme(),
) {
    val rotation = LocalContentRotation.current
    val shownRoll = animateAngle(angle = roll)
    val shownPitch = animateAngle(angle = pitch)
    val rollLevel = animateLevelFraction { LevelZone.isRollLevel(roll = shownRoll.value) }
    val pitchLevel = animateLevelFraction { LevelZone.isPitchLevel(pitch = shownPitch.value) }
    val labels = rememberLevelIndicatorLabels(
        shadowColor = colors.shadowColor,
        shadowRadius = SHADOW_RADIUS,
    )

    LevelHapticEffect(
        roll = shownRoll,
        pitch = shownPitch,
    )
    Spacer(
        modifier = modifier
            .size(size = LEVEL_LINE_LENGTH)
            .drawWithCache {
                val linePaint = LevelLinePaint(
                    shadowColor = colors.shadowColor,
                    shadowRadius = SHADOW_RADIUS.toPx(),
                )

                onDrawBehind {
                    val color = lerp(
                        start = colors.lineColor,
                        stop = colors.levelColor,
                        fraction = pitchLevel.value,
                    )

                    rotate(degrees = rotation()) {
                        drawDashes(
                            linePaint = linePaint,
                            color = color,
                        )
                        rotate(degrees = shownRoll.value) {
                            drawLevelLines(
                                roll = shownRoll.value,
                                pitch = shownPitch.value,
                                rollLevel = rollLevel.value,
                                color = color,
                                labels = labels,
                                linePaint = linePaint,
                                horizonColor = colors.levelColor,
                            )
                        }
                    }
                }
            },
    )
}

private fun DrawScope.drawDashes(
    linePaint: LevelLinePaint,
    color: Color,
) {
    val outer = LEVEL_LINE_LENGTH.toPx() / 2
    val inner = outer - DASH_LENGTH.toPx()

    drawIntoCanvas { canvas ->
        linePaint.drawLine(
            canvas = canvas,
            start = center - Offset(x = outer, y = 0f),
            end = center - Offset(x = inner, y = 0f),
            width = LINE_WIDTH.toPx(),
            color = color,
        )
        linePaint.drawLine(
            canvas = canvas,
            start = center + Offset(x = inner, y = 0f),
            end = center + Offset(x = outer, y = 0f),
            width = LINE_WIDTH.toPx(),
            color = color,
        )
    }
}

private fun DrawScope.drawLevelLines(
    roll: Float,
    pitch: Float,
    rollLevel: Float,
    color: Color,
    labels: LevelIndicatorLabels,
    linePaint: LevelLinePaint,
    horizonColor: Color,
) {
    val halfLength = LINE_LENGTH.toPx() / 2
    val rollHalfLength = lerp(
        start = LINE_LENGTH.toPx(),
        stop = LEVEL_LINE_LENGTH.toPx(),
        fraction = rollLevel,
    ) / 2
    val pitchFraction = pitch.coerceIn(-PITCH_RANGE, PITCH_RANGE) / PITCH_RANGE
    val horizonY = center.y + pitchFraction * HORIZON_SHIFT_RANGE.toPx()

    drawIntoCanvas { canvas ->
        linePaint.drawLine(
            canvas = canvas,
            start = Offset(x = center.x - halfLength, y = horizonY),
            end = Offset(x = center.x + halfLength, y = horizonY),
            width = LINE_WIDTH.toPx(),
            color = horizonColor,
        )
        linePaint.drawLine(
            canvas = canvas,
            start = Offset(x = center.x - rollHalfLength, y = center.y),
            end = Offset(x = center.x + rollHalfLength, y = center.y),
            width = lerp(
                start = LINE_WIDTH.toPx(),
                stop = LEVEL_LINE_WIDTH.toPx(),
                fraction = rollLevel,
            ),
            color = color,
        )
    }
    drawCenteredText(
        text = labels.label(degrees = abs(roll).roundToInt()),
        center = center - Offset(x = 0f, y = LABEL_OFFSET.toPx()),
        color = color,
    )
}

@PreviewLightDark
@Composable
private fun LevelIndicatorPreview() {
    CameraPreviewColumn {
        CameraPreviewViewfinder {
            FlowRow(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                PreviewLevelIndicator(roll = -22f, pitch = -30f)
                PreviewLevelIndicator(roll = -23f, pitch = 0f)
                PreviewLevelIndicator(roll = 0f, pitch = 20f)
                PreviewLevelIndicator(roll = 0f, pitch = 0f)
            }
        }
    }
}

@Composable
private fun PreviewLevelIndicator(
    roll: Float,
    pitch: Float,
) {
    LevelIndicator(
        roll = { roll },
        pitch = { pitch },
    )
}
