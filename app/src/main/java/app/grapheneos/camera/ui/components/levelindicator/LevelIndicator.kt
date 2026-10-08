package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.LocalContentRotation
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.CameraPreviewViewfinder

private val SHADOW_RADIUS = 1.dp

/**
 * Draws around its center, so the caller centers it on the image.
 *
 * @param roll how far the horizon is turned clockwise, in degrees; pointed up or down, the
 * direction the phone leans in.
 * @param pitch how far the camera points above the horizon, in degrees, 90 pointing straight up.
 */
@Composable
internal fun LevelIndicator(
    roll: () -> Float,
    pitch: () -> Float,
    modifier: Modifier = Modifier,
    colors: LevelIndicatorColors = LevelIndicatorColors.fromTheme(),
) {
    val rotation = LocalContentRotation.current
    val motion = rememberLevelIndicatorMotion(
        roll = roll,
        pitch = pitch,
    )
    val labels = rememberLevelIndicatorLabels(
        shadowColor = colors.shadowColor,
        shadowRadius = SHADOW_RADIUS,
    )

    Spacer(
        modifier = modifier
            .size(size = LEVEL_LINE_LENGTH)
            .drawWithCache {
                val paint = LevelPaint(
                    shadowColor = colors.shadowColor,
                    shadowRadius = SHADOW_RADIUS.toPx(),
                )

                onDrawBehind {
                    rotate(degrees = rotation()) {
                        drawLevelIndicator(
                            motion = motion,
                            labels = labels,
                            paint = paint,
                            colors = colors,
                        )
                    }
                }
            },
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
                PreviewLevelIndicator(roll = 150f, pitch = -70f)
                PreviewLevelIndicator(roll = 0f, pitch = -90f)
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
