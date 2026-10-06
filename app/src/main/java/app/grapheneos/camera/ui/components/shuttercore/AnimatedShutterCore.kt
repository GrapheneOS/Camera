package app.grapheneos.camera.ui.components.shuttercore

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.grapheneos.camera.ui.components.motion.MORPH_SPEC
import app.grapheneos.camera.ui.components.motion.SETTLE_COLOR_SPEC
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterCore
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterTone

@Composable
internal fun AnimatedShutterCore(
    core: ShutterCore,
    tone: ShutterTone,
    modifier: Modifier = Modifier,
    colors: ShutterCoreColors = ShutterCoreColors.fromTheme(),
) {
    val shape = animateShutterCoreShape(core = core)
    val color by animateColorAsState(
        targetValue = colors.color(tone),
        animationSpec = SETTLE_COLOR_SPEC,
    )

    Canvas(modifier = modifier) {
        drawShutterCore(
            color = color,
            coreSize = size.minDimension * shape.sizeFraction,
            cornerFraction = shape.cornerFraction,
        )
    }
}

@Composable
internal fun animateShutterCoreShape(core: ShutterCore): ShutterCoreShape {
    return ShutterCoreShape(
        sizeFraction = animateFloatAsState(
            targetValue = core.sizeFraction,
            animationSpec = MORPH_SPEC,
        ),
        cornerFraction = animateFloatAsState(
            targetValue = core.cornerFraction,
            animationSpec = MORPH_SPEC,
        ),
    )
}

internal fun DrawScope.drawShutterCore(
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

@Stable
internal class ShutterCoreShape(
    sizeFraction: State<Float>,
    cornerFraction: State<Float>,
) {
    val sizeFraction by sizeFraction
    val cornerFraction by cornerFraction
}
