package app.grapheneos.camera.ui.components.zoom

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.animateEnabledAlpha
import app.grapheneos.camera.ui.components.ruler.RULER_SIZE
import app.grapheneos.camera.ui.components.ruler.RulerMetrics
import app.grapheneos.camera.ui.components.ruler.rememberRulerBindings
import app.grapheneos.camera.ui.components.ruler.rememberRulerMetrics
import app.grapheneos.camera.ui.components.ruler.rulerInput
import app.grapheneos.camera.ui.components.zoom.gesture.ZoomBarState
import app.grapheneos.camera.ui.components.zoom.gesture.rememberZoomBarState
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.TABULAR_FIGURES

private val LABEL_SIZE = 14.dp

/**
 * A logarithmic ruler: the indicator stays in the center and the scale moves under it.
 * [onValueChange] is called continuously during a drag, without snapping, and
 * [onValueChangeFinished] when it ends; any other change of [value] is animated to. [stops] get a
 * major mark, a label and a haptic tick, and a drag holds on a stop until pulled a tick past it.
 */
@Composable
internal fun ZoomBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    stops: List<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
    colors: ZoomBarColors = ZoomBarColors.fromTheme(),
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
    val alpha by animateEnabledAlpha(enabled = enabled)

    ZoomBarLayers(
        state = state,
        scale = scale,
        metrics = metrics,
        colors = colors,
        interactionSource = resolvedInteractionSource,
        modifier = modifier
            .size(size = RULER_SIZE)
            .graphicsLayer { this.alpha = alpha }
            .rulerInput(
                state = state,
                rangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange,
                ),
                enabled = enabled,
                layoutDirection = LocalLayoutDirection.current,
                interactionSource = resolvedInteractionSource,
            ),
    )
}

@Composable
private fun ZoomBarLayers(
    state: ZoomBarState,
    scale: ZoomBarScale,
    metrics: RulerMetrics,
    colors: ZoomBarColors,
    interactionSource: InteractionSource,
    modifier: Modifier = Modifier,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val labels = rememberZoomBarLabels(
        stops = scale.stops,
        format = rememberZoomFormat(),
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = with(LocalDensity.current) { LABEL_SIZE.toSp() },
            fontFeatureSettings = TABULAR_FIGURES,
        ),
    )

    Spacer(
        modifier = modifier.drawWithCache {
            val window = zoomBarWindow(height = size.height)

            onDrawBehind {
                drawZoomBar(
                    position = state.position,
                    scale = scale,
                    labels = labels,
                    window = window,
                    isFocused = isFocused,
                    metrics = metrics,
                    colors = colors,
                )
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun ZoomBarPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewZoomBar(value = 1f)
            PreviewZoomBar(value = 2.7f)
            PreviewZoomBar(value = 0.6f)
            PreviewZoomBar(
                value = 5f,
                valueRange = 0.5f..10f,
                stops = listOf(0.5f, 1f, 2f, 5f, 10f),
            )
            PreviewZoomBar(
                value = 1f,
                enabled = false,
            )
        }
    }
}

@Composable
private fun PreviewZoomBar(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0.5f..8f,
    stops: List<Float> = listOf(0.5f, 1f, 2f, 8f),
    enabled: Boolean = true,
) {
    var currentValue by remember { mutableFloatStateOf(value) }

    ZoomBar(
        value = currentValue,
        onValueChange = { currentValue = it },
        valueRange = valueRange,
        stops = stops,
        enabled = enabled,
    )
}
