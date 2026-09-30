package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonCore
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonProgress
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTone
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_CLOSE_ICON

private val BUTTON_SIZE = 84.dp
private val ICON_SIZE = 24.dp
private val FOCUS_RING_WIDTH = 3.dp
private val PROGRESS_PADDING = 4.dp
private val PROGRESS_GAP = 4.dp
private val PROGRESS_INSET = PROGRESS_PADDING +
    ProgressIndicatorDefaults.CircularStrokeWidth +
    PROGRESS_GAP

private const val PRESSED_SCALE = 1.1f
private const val PRESSED_ALPHA = 0.8f
private const val DISABLED_ALPHA = 0.35f

private val MORPH_SPEC = spring<Float>(
    dampingRatio = 0.7f,
    stiffness = 500f,
)

@Composable
internal fun CaptureButton(
    onClick: () -> Unit,
    core: CaptureButtonCore,
    modifier: Modifier = Modifier,
    tone: CaptureButtonTone = CaptureButtonTone.Neutral,
    enabled: Boolean = true,
    progress: CaptureButtonProgress = CaptureButtonProgress.None,
    icon: ImageVector? = null,
    colors: CaptureButtonColors = CaptureButtonColors.fromTheme(),
    interactionSource: MutableInteractionSource? = null,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()
    val isFocused by resolvedInteractionSource.collectIsFocusedAsState()
    val alpha by animateEnabledAlpha(enabled = enabled)

    Box(
        modifier = modifier
            .size(BUTTON_SIZE)
            .graphicsLayer {
                this.alpha = alpha
            }
            .clickable(
                interactionSource = resolvedInteractionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CaptureButtonFace(
            core = core,
            isPressed = isPressed,
            isFocused = isFocused,
            hasProgress = progress != CaptureButtonProgress.None,
            coreColor = colors.coreColor(tone),
            containerColor = colors.containerColor,
            focusColor = colors.focusColor,
            modifier = Modifier.fillMaxSize(),
        )
        CaptureButtonProgressIndicator(
            progress = progress,
            color = colors.progressColor,
            trackColor = colors.progressTrackColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(all = PROGRESS_PADDING),
        )
        CaptureButtonIcon(
            icon = icon,
            isPressed = isPressed,
            tint = colors.contentColor(core),
        )
    }
}

@Composable
private fun CaptureButtonFace(
    core: CaptureButtonCore,
    isPressed: Boolean,
    isFocused: Boolean,
    hasProgress: Boolean,
    coreColor: Color,
    containerColor: Color,
    focusColor: Color,
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
    )

    Canvas(modifier = modifier) {
        val progressInset = PROGRESS_INSET.toPx() * progressInsetFraction
        val maxCoreSize = size.minDimension - progressInset * 2

        drawCircle(
            color = containerColor,
        )
        drawCore(
            color = animatedCoreColor,
            sizeFraction = sizeFraction,
            pressedScale = pressedScale,
            cornerFraction = cornerFraction,
            maxSize = maxCoreSize,
        )
        if (isFocused) {
            drawFocusRing(color = focusColor)
        }
    }
}

@Composable
private fun CaptureButtonIcon(
    icon: ImageVector?,
    isPressed: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val pressedScale by animatePressedScale(isPressed = isPressed)
    val animatedTint by animateColorAsState(targetValue = tint)

    Crossfade(
        targetState = icon,
        modifier = modifier.graphicsLayer {
            scaleX = pressedScale
            scaleY = pressedScale
        },
    ) { targetIcon ->
        if (targetIcon != null) {
            Icon(
                imageVector = targetIcon,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
                tint = animatedTint,
            )
        }
    }
}

@Composable
private fun animatePressedScale(
    isPressed: Boolean,
): State<Float> {
    return animateFloatAsState(
        targetValue = when {
            isPressed -> PRESSED_SCALE
            else -> 1f
        },
        animationSpec = MORPH_SPEC,
    )
}

@Composable
private fun animatePressedColor(
    color: Color,
    isPressed: Boolean,
): State<Color> {
    return animateColorAsState(
        targetValue = when {
            isPressed -> color.copy(alpha = color.alpha * PRESSED_ALPHA)
            else -> color
        },
    )
}

@Composable
private fun animateEnabledAlpha(
    enabled: Boolean,
): State<Float> {
    return animateFloatAsState(
        targetValue = when {
            enabled -> 1f
            else -> DISABLED_ALPHA
        },
    )
}

private fun DrawScope.drawCore(
    color: Color,
    sizeFraction: Float,
    pressedScale: Float,
    cornerFraction: Float,
    maxSize: Float,
) {
    val coreSize = (size.minDimension * sizeFraction).coerceAtMost(maxSize) * pressedScale

    drawRoundRect(
        color = color,
        topLeft = center - Offset(x = coreSize / 2, y = coreSize / 2),
        size = Size(width = coreSize, height = coreSize),
        cornerRadius = CornerRadius(coreSize * cornerFraction),
    )
}

private fun DrawScope.drawFocusRing(
    color: Color,
) {
    val width = FOCUS_RING_WIDTH.toPx()

    drawCircle(
        color = color,
        radius = (size.minDimension - width) / 2,
        style = Stroke(width = width),
    )
}

@PreviewLightDark
@Composable
private fun CaptureButtonPreview() {
    CameraPreviewColumn {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewCaptureButton(
                core = CaptureButtonCore.Disc,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Dot,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Square,
                tone = CaptureButtonTone.Recording,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Dot,
                tone = CaptureButtonTone.Recording,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.None,
                icon = PREVIEW_CLOSE_ICON,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Disc,
                icon = PREVIEW_CLOSE_ICON,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Disc,
                enabled = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun CaptureButtonProgressPreview() {
    CameraPreviewColumn {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewCaptureButton(
                core = CaptureButtonCore.Disc,
                progress = CaptureButtonProgress.Indeterminate,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.Disc,
                progress = CaptureButtonProgress.Determinate(
                    fraction = 0.6f,
                ),
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.None,
                progress = CaptureButtonProgress.Segmented(
                    segments = 10,
                    filled = 7,
                ),
                icon = PREVIEW_CLOSE_ICON,
            )
            PreviewCaptureButton(
                core = CaptureButtonCore.None,
                progress = CaptureButtonProgress.Segmented(
                    segments = 3,
                    filled = 2,
                ),
                icon = PREVIEW_CLOSE_ICON,
            )
        }
    }
}

@Composable
private fun PreviewCaptureButton(
    core: CaptureButtonCore,
    tone: CaptureButtonTone = CaptureButtonTone.Neutral,
    enabled: Boolean = true,
    progress: CaptureButtonProgress = CaptureButtonProgress.None,
    icon: ImageVector? = null,
) {
    CaptureButton(
        onClick = {},
        core = core,
        tone = tone,
        enabled = enabled,
        progress = progress,
        icon = icon,
    )
}
