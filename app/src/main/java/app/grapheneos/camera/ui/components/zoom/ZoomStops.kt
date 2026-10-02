package app.grapheneos.camera.ui.components.zoom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.grapheneos.camera.ui.components.pillselector.PillSelector
import app.grapheneos.camera.ui.components.pillselector.PillSelectorColors
import app.grapheneos.camera.ui.components.pillselector.PillSelectorItemScope
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.TABULAR_FIGURES

private val STOP_SIZE = 48.dp
private val LABEL_SIZE = 16.dp

private const val SELECTED_LABEL_SCALE = 1.15f

/**
 * The selected stop is the last one at or below [value], or the first one below all of them. It
 * shows [value] followed by [valueSuffix]; the others show only their number. [onStopClick] reports
 * a tapped stop, and [value] changes only when the caller sets it.
 */
@Composable
internal fun ZoomStops(
    value: Float,
    stops: List<Float>,
    valueSuffix: String,
    onStopClick: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    customActions: List<CustomAccessibilityAction> = emptyList(),
    colors: PillSelectorColors = PillSelectorColors.fromTheme(),
) {
    val stopList = remember(stops) { ZoomStopList(stops = stops) }
    val selectedIndex = stopList.selectedIndex(value = value)
    val format = rememberZoomFormat()
    val style = MaterialTheme.typography.labelLarge.copy(
        fontSize = with(LocalDensity.current) { LABEL_SIZE.toSp() },
        lineHeight = TextUnit.Unspecified,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    PillSelector(
        selectedIndex = selectedIndex,
        itemCount = stops.size,
        onItemClick = { index -> onStopClick(stops[index]) },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    ) { index ->
        val isSelected = index == selectedIndex
        val number = format.format(
            value = when {
                isSelected -> value
                else -> stops[index]
            },
        )

        ZoomStopLabel(
            text = when {
                isSelected -> number + valueSuffix
                else -> number
            },
            description = number + valueSuffix,
            customActions = customActions,
            style = when {
                isSelected -> style.copy(fontWeight = FontWeight.Bold)
                else -> style
            },
            scope = this,
        )
    }
}

@Composable
private fun ZoomStopLabel(
    text: String,
    description: String,
    customActions: List<CustomAccessibilityAction>,
    style: TextStyle,
    scope: PillSelectorItemScope,
) {
    Box(
        modifier = Modifier
            .size(STOP_SIZE)
            .semantics {
                contentDescription = description
                this.customActions = customActions
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            modifier = Modifier
                .wrapContentWidth(unbounded = true)
                .graphicsLayer {
                    val scale = lerp(
                        start = 1f,
                        stop = SELECTED_LABEL_SCALE,
                        fraction = scope.emphasis(),
                    )

                    scaleX = scale
                    scaleY = scale
                }
                .clearAndSetSemantics {},
            style = style,
            softWrap = false,
            maxLines = 1,
            color = scope.contentColor,
        )
    }
}

@PreviewLightDark
@Composable
private fun ZoomStopsPreview() {
    CameraPreviewColumn {
        ZoomStopsPreviewStates()
    }
}

@Preview(fontScale = 2f)
@Composable
private fun ZoomStopsLargeFontPreview() {
    CameraPreviewColumn {
        ZoomStopsPreviewStates()
    }
}

@Composable
private fun ZoomStopsPreviewStates() {
    Column(
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        PreviewZoomStops(value = 0.6f)
        PreviewZoomStops(value = 1f)
        PreviewZoomStops(value = 4.5f)
        PreviewZoomStops(
            value = 9.9f,
            stops = listOf(0.5f, 1f, 2f, 5f, 10f),
        )
        PreviewZoomStops(
            value = 1f,
            enabled = false,
        )
    }
}

@Composable
private fun PreviewZoomStops(
    value: Float,
    stops: List<Float> = listOf(0.5f, 1f, 2f),
    enabled: Boolean = true,
) {
    var currentValue by remember { mutableFloatStateOf(value) }

    ZoomStops(
        value = currentValue,
        stops = stops,
        valueSuffix = "×",
        onStopClick = { stop -> currentValue = stop },
        enabled = enabled,
    )
}
