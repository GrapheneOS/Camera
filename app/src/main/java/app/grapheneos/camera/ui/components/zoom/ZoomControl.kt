package app.grapheneos.camera.ui.components.zoom

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.animateEnabledAlpha
import app.grapheneos.camera.ui.components.pillselector.PillSelectorColors
import app.grapheneos.camera.ui.components.ruler.RULER_SIZE
import app.grapheneos.camera.ui.components.ruler.RulerMetrics
import app.grapheneos.camera.ui.components.ruler.rememberRulerBindings
import app.grapheneos.camera.ui.components.ruler.rememberRulerMetrics
import app.grapheneos.camera.ui.components.ruler.rulerControls
import app.grapheneos.camera.ui.components.ruler.rulerDrag
import app.grapheneos.camera.ui.components.zoom.gesture.ZoomBarState
import app.grapheneos.camera.ui.components.zoom.gesture.rememberZoomBarState
import app.grapheneos.camera.ui.core.CameraPreviewColumn

private val MORPH_FADE_SPEC = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 500f,
)
private val MORPH_SIZE_SPEC = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 500f,
)

/**
 * Collapsed, it shows [stops], and a tap on one reports it through [onStopClick]. A horizontal
 * swipe on the stops calls [onExpand] and goes on moving the value with the same touch, as the
 * expanded bar does. [expanded] belongs to the caller: it expands on [onExpand] and collapses
 * whenever the screen decides to. [expandLabel] names the accessibility action that expands the
 * stops, the only way to the bar without a swipe.
 */
@Composable
internal fun ZoomControl(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    stops: List<Float>,
    valueSuffix: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onStopClick: (Float) -> Unit,
    expandLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
    barColors: ZoomBarColors = ZoomBarColors.fromTheme(),
    stopsColors: PillSelectorColors = PillSelectorColors.fromTheme(),
    interactionSource: MutableInteractionSource? = null,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val scale = remember(valueRange, stops) {
        ZoomBarScale(
            valueRange = valueRange,
            stops = stops,
        )
    }
    val metrics = rememberRulerMetrics()
    val state = rememberZoomBarState(
        value = value,
        scale = scale,
        bindings = rememberRulerBindings(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            tickSpacing = metrics.tickSpacing,
        ),
    )
    val currentExpanded = rememberUpdatedState(expanded)
    val currentOnExpand = rememberUpdatedState(onExpand)
    val alpha by animateEnabledAlpha(enabled = enabled)

    ZoomControlLayers(
        value = value,
        valueRange = valueRange,
        stops = stops,
        valueSuffix = valueSuffix,
        expanded = expanded,
        onExpand = { currentOnExpand.value() },
        onStopClick = onStopClick,
        expandLabel = expandLabel,
        enabled = enabled,
        alpha = { alpha },
        state = state,
        scale = scale,
        metrics = metrics,
        barColors = barColors,
        stopsColors = stopsColors,
        interactionSource = resolvedInteractionSource,
        modifier = modifier.rulerDrag(
            state = state,
            enabled = enabled,
            layoutDirection = LocalLayoutDirection.current,
            interactionSource = resolvedInteractionSource,
            onDragStarted = {
                if (!currentExpanded.value) {
                    currentOnExpand.value()
                }
            },
        ),
    )
}

@Composable
private fun ZoomControlLayers(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    stops: List<Float>,
    valueSuffix: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onStopClick: (Float) -> Unit,
    expandLabel: String,
    enabled: Boolean,
    alpha: () -> Float,
    state: ZoomBarState,
    scale: ZoomBarScale,
    metrics: RulerMetrics,
    barColors: ZoomBarColors,
    stopsColors: PillSelectorColors,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { this.alpha = alpha() }
                .background(
                    color = barColors.containerColor,
                    shape = CircleShape,
                ),
        )
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                fadeIn(animationSpec = MORPH_FADE_SPEC) togetherWith
                    fadeOut(animationSpec = MORPH_FADE_SPEC) using
                    SizeTransform { _, _ -> MORPH_SIZE_SPEC }
            },
            contentAlignment = Alignment.Center,
        ) { isExpanded ->
            when {
                isExpanded -> ZoomControlBar(
                    value = value,
                    valueRange = valueRange,
                    state = state,
                    scale = scale,
                    metrics = metrics,
                    enabled = enabled,
                    alpha = alpha,
                    colors = barColors,
                    interactionSource = interactionSource,
                )

                else -> ZoomControlStops(
                    value = value,
                    stops = stops,
                    valueSuffix = valueSuffix,
                    onExpand = onExpand,
                    onStopClick = onStopClick,
                    expandLabel = expandLabel,
                    enabled = enabled,
                    colors = stopsColors,
                )
            }
        }
    }
}

@Composable
private fun ZoomControlStops(
    value: Float,
    stops: List<Float>,
    valueSuffix: String,
    onExpand: () -> Unit,
    onStopClick: (Float) -> Unit,
    expandLabel: String,
    enabled: Boolean,
    colors: PillSelectorColors,
) {
    ZoomStops(
        value = value,
        stops = stops,
        valueSuffix = valueSuffix,
        onStopClick = onStopClick,
        enabled = enabled,
        customActions = listOf(
            CustomAccessibilityAction(label = expandLabel) {
                onExpand()
                true
            },
        ),
        colors = colors.copy(containerColor = Color.Transparent),
    )
}

@Composable
private fun ZoomControlBar(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    state: ZoomBarState,
    scale: ZoomBarScale,
    metrics: RulerMetrics,
    enabled: Boolean,
    alpha: () -> Float,
    colors: ZoomBarColors,
    interactionSource: MutableInteractionSource,
) {
    ZoomBarLayers(
        state = state,
        scale = scale,
        metrics = metrics,
        colors = colors.copy(containerColor = Color.Transparent),
        interactionSource = interactionSource,
        modifier = Modifier
            .size(size = RULER_SIZE)
            .graphicsLayer { this.alpha = alpha() }
            .rulerControls(
                state = state,
                rangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange,
                ),
                enabled = enabled,
                layoutDirection = LocalLayoutDirection.current,
                interactionSource = interactionSource,
            ),
    )
}

@PreviewLightDark
@Composable
private fun ZoomControlPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewZoomControl(expanded = false)
            PreviewZoomControl(expanded = true)
            PreviewZoomControl(
                expanded = false,
                enabled = false,
            )
        }
    }
}

@Composable
private fun PreviewZoomControl(
    expanded: Boolean,
    enabled: Boolean = true,
) {
    var currentValue by remember { mutableFloatStateOf(1.4f) }
    var isExpanded by remember { mutableStateOf(expanded) }

    ZoomControl(
        value = currentValue,
        onValueChange = { newValue -> currentValue = newValue },
        valueRange = 0.5f..8f,
        stops = listOf(0.5f, 1f, 2f),
        valueSuffix = "×",
        expanded = isExpanded,
        onExpand = { isExpanded = true },
        onStopClick = { stop -> currentValue = stop },
        expandLabel = "Adjust zoom",
        enabled = enabled,
    )
}
