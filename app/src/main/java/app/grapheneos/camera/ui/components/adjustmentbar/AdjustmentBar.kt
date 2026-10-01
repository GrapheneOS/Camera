package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.adjustmentbar.gesture.AdjustmentBarState
import app.grapheneos.camera.ui.components.adjustmentbar.gesture.adjustmentBarInput
import app.grapheneos.camera.ui.components.adjustmentbar.gesture.rememberAdjustmentBarState
import app.grapheneos.camera.ui.components.motion.animateEnabledAlpha
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_BRIGHTNESS_HIGH_ICON
import app.grapheneos.camera.ui.core.PREVIEW_BRIGHTNESS_LOW_ICON
import app.grapheneos.camera.ui.core.PREVIEW_COOL_TINT
import app.grapheneos.camera.ui.core.PREVIEW_WARM_TINT

private val BAR_WIDTH = 284.dp
private val BAR_HEIGHT = 52.dp

/**
 * The indicator stays in the center and the scale moves under it. [onValueChange] is called for
 * every tick that passes the indicator during a drag, and for the nearest tick on release before
 * [onValueChangeFinished]. Tapping or holding [startIcon] or [endIcon] steps toward that end of
 * [valueRange]. Any change of [value] that is not a drag, such as a step or a reset, is animated to.
 */
@Composable
internal fun AdjustmentBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    majorTickInterval: Int = 4,
    startIcon: ImageVector? = null,
    endIcon: ImageVector? = null,
    onValueChangeFinished: () -> Unit = {},
    colors: AdjustmentBarColors = AdjustmentBarColors.fromTheme(),
    interactionSource: MutableInteractionSource? = null,
) {
    require(majorTickInterval > 0) {
        "majorTickInterval must be positive, was $majorTickInterval"
    }

    val density = LocalDensity.current
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val scale = remember(valueRange, steps) {
        AdjustmentBarScale(
            valueRange = valueRange,
            steps = steps,
        )
    }
    val metrics = remember(density) {
        AdjustmentBarMetrics(density = density)
    }
    val state = rememberAdjustmentBarState(
        value = value,
        scale = scale,
        tickSpacing = metrics.tickSpacing,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
    )
    val tint = rememberAdjustmentBarTint(colors = colors)
    val alpha by animateEnabledAlpha(enabled = enabled)

    Box(
        modifier = modifier
            .size(
                width = BAR_WIDTH,
                height = BAR_HEIGHT,
            )
            .graphicsLayer { this.alpha = alpha }
            .adjustmentBarInput(
                value = value,
                scale = scale,
                state = state,
                enabled = enabled,
                isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl,
                interactionSource = resolvedInteractionSource,
            ),
    ) {
        AdjustmentBarLayers(
            state = state,
            enabled = enabled,
            scale = scale,
            majorTickInterval = majorTickInterval,
            startIcon = startIcon,
            endIcon = endIcon,
            tint = tint,
            metrics = metrics,
            colors = colors,
            interactionSource = resolvedInteractionSource,
        )
    }
}

@Composable
private fun BoxScope.AdjustmentBarLayers(
    state: AdjustmentBarState,
    enabled: Boolean,
    scale: AdjustmentBarScale,
    majorTickInterval: Int,
    startIcon: ImageVector?,
    endIcon: ImageVector?,
    tint: AdjustmentBarTint,
    metrics: AdjustmentBarMetrics,
    colors: AdjustmentBarColors,
    interactionSource: InteractionSource,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawAdjustmentBar(
            position = state.position,
            scale = scale,
            majorTickInterval = majorTickInterval,
            hasStartIcon = startIcon != null,
            hasEndIcon = endIcon != null,
            isFocused = isFocused,
            tint = tint,
            metrics = metrics,
            colors = colors,
        )
    }
    if (startIcon != null) {
        AdjustmentBarIcon(
            icon = startIcon,
            tint = colors.startIconColor,
            enabled = enabled,
            onStep = { state.stepWithFeedback(ticks = -1) },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(BAR_HEIGHT),
        )
    }
    if (endIcon != null) {
        AdjustmentBarIcon(
            icon = endIcon,
            tint = colors.endIconColor,
            enabled = enabled,
            onStep = { state.stepWithFeedback(ticks = 1) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(BAR_HEIGHT),
        )
    }
}

@PreviewLightDark
@Composable
private fun AdjustmentBarPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewAdjustmentBar(value = 0f)
            PreviewAdjustmentBar(value = 7f)
            PreviewAdjustmentBar(value = -12f)
            PreviewAdjustmentBar(
                value = 2f,
                hasIcons = false,
            )
            PreviewAdjustmentBar(
                value = 0f,
                enabled = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun AdjustmentBarTintPreview() {
    CameraPreviewColumn {
        val colors = AdjustmentBarColors.fromTheme().copy(
            startTint = PREVIEW_COOL_TINT,
            endTint = PREVIEW_WARM_TINT,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewAdjustmentBar(
                value = -6f,
                colors = colors,
            )
            PreviewAdjustmentBar(
                value = 6f,
                colors = colors,
            )
            PreviewAdjustmentBar(
                value = 12f,
                colors = colors,
            )
        }
    }
}

@Composable
private fun PreviewAdjustmentBar(
    value: Float,
    hasIcons: Boolean = true,
    enabled: Boolean = true,
    colors: AdjustmentBarColors = AdjustmentBarColors.fromTheme(),
) {
    var currentValue by remember { mutableFloatStateOf(value) }

    AdjustmentBar(
        value = currentValue,
        onValueChange = { currentValue = it },
        valueRange = -12f..12f,
        steps = 23,
        enabled = enabled,
        startIcon = PREVIEW_BRIGHTNESS_LOW_ICON.takeIf { hasIcons },
        endIcon = PREVIEW_BRIGHTNESS_HIGH_ICON.takeIf { hasIcons },
        colors = colors,
    )
}
