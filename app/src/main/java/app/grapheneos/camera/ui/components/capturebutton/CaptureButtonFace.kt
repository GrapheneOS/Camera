package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonCore
import app.grapheneos.camera.ui.components.motion.MORPH_SPEC

private val FOCUS_RING_WIDTH = 3.dp

@Composable
internal fun CaptureButtonFace(
    core: CaptureButtonCore,
    isPressed: Boolean,
    isHeld: Boolean,
    isFocused: Boolean,
    hasProgress: Boolean,
    coreColor: Color,
    containerColor: Color,
    focusColor: Color,
    pull: () -> Offset,
    dockProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val sizeFraction by animateFloatAsState(
        targetValue = core.sizeFraction,
        animationSpec = MORPH_SPEC,
    )
    val cornerFraction by animateFloatAsState(
        targetValue = core.cornerFraction,
        animationSpec = MORPH_SPEC,
    )
    val pressedScale by animatePressedScale(
        isPressed = isPressed && !hasProgress,
        isHeld = isHeld && !hasProgress,
    )
    val progressInsetFraction by animateFloatAsState(
        targetValue = when {
            hasProgress -> 1f
            else -> 0f
        },
        animationSpec = MORPH_SPEC,
    )
    val animatedCoreColor by animatePressedColor(
        color = coreColor,
        isPressed = isPressed,
        isHeld = isHeld,
    )

    Canvas(modifier = modifier) {
        val progressInset = PROGRESS_INSET.toPx() * progressInsetFraction
        val restingSize = (size.minDimension * sizeFraction)
            .coerceAtMost(size.minDimension - progressInset * 2)
        val currentDockProgress = dockProgress()
        val coreSize = dockCoreSize(
            size = restingSize * pressedScale,
            dockedSize = dockedCoreSize(
                progress = currentDockProgress,
                density = this,
            ),
            progress = currentDockProgress,
        )

        drawCircle(color = containerColor)
        translate(
            left = pull().x,
            top = pull().y,
        ) {
            drawCore(
                color = animatedCoreColor,
                coreSize = coreSize,
                cornerFraction = cornerFraction,
            )
        }
        if (isFocused) {
            drawFocusRing(color = focusColor)
        }
    }
}

private fun DrawScope.drawCore(
    color: Color,
    coreSize: Float,
    cornerFraction: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = center - Offset(x = coreSize / 2, y = coreSize / 2),
        size = Size(width = coreSize, height = coreSize),
        cornerRadius = CornerRadius(coreSize * cornerFraction),
    )
}

private fun DrawScope.drawFocusRing(color: Color) {
    val width = FOCUS_RING_WIDTH.toPx()

    drawCircle(
        color = color,
        radius = (size.minDimension - width) / 2,
        style = Stroke(width = width),
    )
}
