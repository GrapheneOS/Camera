package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.animation.SETTLE_COLOR_SPEC
import app.grapheneos.camera.ui.core.animation.SETTLE_SPEC
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn
import kotlin.math.roundToInt

private val TILE_HORIZONTAL_PADDING = 16.dp
private val TILE_VERTICAL_PADDING = 12.dp

@Composable
internal fun SettingsTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsTileLayout(
        title = title,
        subtitle = subtitle,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsSliderTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    value: Float,
    onValueChange: (Float) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val scale = SliderScale(
        valueRange = valueRange,
        steps = steps,
    )
    val currentValue by rememberUpdatedState(value)
    val reveal by animateFloatAsState(
        targetValue = when {
            selected -> 1f
            else -> 0f
        },
        animationSpec = SETTLE_SPEC,
        label = "settings_slider_tile_reveal",
    )
    val dragState = rememberSliderDragState(
        value = value,
        scale = scale,
        onValueChange = onValueChange,
    )

    SettingsTileLayout(
        title = title,
        subtitle = subtitle,
        selected = selected,
        onClick = onClick,
        fill = { scale.fraction(currentValue) * reveal },
        modifier = modifier
            .sliderDrag(
                state = dragState,
                enabled = selected,
                reverseDirection = LocalLayoutDirection.current == LayoutDirection.Rtl,
            )
            .sliderSemantics(
                title = title,
                subtitle = subtitle,
                value = value,
                scale = scale,
                enabled = selected,
                onValueChange = onValueChange,
            ),
    )
}

@Composable
private fun SettingsTileLayout(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: (() -> Float)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = when {
            selected && fill == null -> colorScheme.primaryContainer
            else -> colorScheme.surfaceContainerHighest
        },
        animationSpec = SETTLE_COLOR_SPEC,
        label = "settings_tile_container",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            selected && fill == null -> colorScheme.onPrimaryContainer
            else -> colorScheme.onSurface
        },
        animationSpec = SETTLE_COLOR_SPEC,
        label = "settings_tile_content",
    )

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .then(
                when (fill) {
                    null -> Modifier
                    else -> Modifier.drawFill(
                        fill = fill,
                        color = colorScheme.primaryContainer,
                    )
                },
            )
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = when (fill) {
                    null -> LocalIndication.current
                    else -> null
                },
                onClick = onClick,
                role = Role.RadioButton,
            ),
        propagateMinConstraints = true,
    ) {
        SettingsTileTexts(
            title = title,
            subtitle = subtitle,
            color = contentColor,
            fill = fill,
        )
    }
}

@Composable
private fun SettingsTileTexts(
    title: String,
    subtitle: String,
    color: Color,
    fill: (() -> Float)?,
) {
    SettingsTileText(
        title = title,
        subtitle = subtitle,
        color = color,
        modifier = when (fill) {
            null -> Modifier
            else -> Modifier.clipToFill(
                fill = fill,
                clipOp = ClipOp.Difference,
            )
        },
    )
    if (fill != null) {
        SettingsTileText(
            title = title,
            subtitle = subtitle,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.clipToFill(
                fill = fill,
                clipOp = ClipOp.Intersect,
            ),
        )
    }
}

@Composable
private fun SettingsTileText(
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(
            horizontal = TILE_HORIZONTAL_PADDING,
            vertical = TILE_VERTICAL_PADDING,
        ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
private fun rememberSliderDragState(
    value: Float,
    scale: SliderScale,
    onValueChange: (Float) -> Unit,
): SliderDragState {
    val currentValue = rememberUpdatedState(value)
    val currentScale = rememberUpdatedState(scale)
    val currentOnValueChange = rememberUpdatedState(onValueChange)

    return remember {
        SliderDragState(
            value = currentValue,
            scale = currentScale,
            onValueChange = currentOnValueChange,
        )
    }
}

private fun Modifier.sliderDrag(
    state: SliderDragState,
    enabled: Boolean,
    reverseDirection: Boolean,
): Modifier {
    return onSizeChanged { state.width = it.width }
        .draggable(
            state = state.draggableState,
            orientation = Orientation.Horizontal,
            enabled = enabled,
            reverseDirection = reverseDirection,
            onDragStarted = { state.startDrag() },
        )
}

private fun Modifier.sliderSemantics(
    title: String,
    subtitle: String,
    value: Float,
    scale: SliderScale,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
): Modifier {
    return clearAndSetSemantics {
        contentDescription = title
        stateDescription = subtitle
        progressBarRangeInfo = ProgressBarRangeInfo(
            current = value,
            range = scale.valueRange,
            steps = scale.steps,
        )
        if (enabled) {
            setProgress { target ->
                onValueChange(scale.snap(target))
                true
            }
        }
    }
}

private fun Modifier.drawFill(
    fill: () -> Float,
    color: Color,
): Modifier {
    return drawBehind {
        drawRect(
            color = color,
            topLeft = fillOffset(fill()),
            size = Size(
                width = size.width * fill(),
                height = size.height,
            ),
        )
    }
}

private fun Modifier.clipToFill(
    fill: () -> Float,
    clipOp: ClipOp,
): Modifier {
    return drawWithContent {
        val left = fillOffset(fill()).x
        clipRect(
            left = left,
            right = left + size.width * fill(),
            clipOp = clipOp,
        ) {
            this@drawWithContent.drawContent()
        }
    }
}

private fun DrawScope.fillOffset(fraction: Float): Offset {
    return when (layoutDirection) {
        LayoutDirection.Ltr -> Offset.Zero
        LayoutDirection.Rtl -> Offset(x = size.width * (1 - fraction), y = 0f)
    }
}

private class SliderDragState(
    private val value: State<Float>,
    private val scale: State<SliderScale>,
    private val onValueChange: State<(Float) -> Unit>,
) {
    var width = 0
    private var dragValue = 0f

    val draggableState = DraggableState { delta ->
        if (width > 0) {
            val scale = scale.value
            dragValue = scale.coerce(dragValue + delta / width * scale.span)
            onValueChange.value(scale.snap(dragValue))
        }
    }

    fun startDrag() {
        dragValue = value.value
    }
}

private class SliderScale(
    val valueRange: ClosedFloatingPointRange<Float>,
    val steps: Int,
) {
    val span = valueRange.endInclusive - valueRange.start
    private val stepSize = span / (steps + 1)

    fun coerce(value: Float): Float {
        return value.coerceIn(valueRange)
    }

    fun snap(value: Float): Float {
        val stepsFromStart = (coerce(value) - valueRange.start) / stepSize

        return when (steps) {
            0 -> coerce(value)
            else -> valueRange.start + stepsFromStart.roundToInt() * stepSize
        }
    }

    // Each step owns a slice of the fill, so the first value is a thin line rather than nothing.
    fun fraction(value: Float): Float {
        return when (steps) {
            0 -> (value - valueRange.start) / span
            else -> (value - valueRange.start + stepSize) / (span + stepSize)
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingsTilesPreview() {
    CameraPreviewColumn {
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsTile(
                title = "Standard",
                subtitle = "90%",
                selected = false,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            SettingsSliderTile(
                title = "Custom",
                subtitle = "63%",
                selected = true,
                value = 63f,
                onValueChange = {},
                onClick = {},
                valueRange = 1f..100f,
                steps = 98,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
