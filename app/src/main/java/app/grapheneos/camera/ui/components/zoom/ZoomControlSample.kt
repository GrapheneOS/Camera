package app.grapheneos.camera.ui.components.zoom

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.POP_IN
import app.grapheneos.camera.ui.components.motion.POP_OUT
import app.grapheneos.camera.ui.components.valuechip.ValueChip
import app.grapheneos.camera.ui.core.CameraPreviewControl
import app.grapheneos.camera.ui.core.CameraPreviewSample
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private const val ZOOM_SUFFIX = "×"
private const val PINCH_FACTOR = 1.25f

private val COLLAPSE_DELAY = 1.seconds

private val THREE_STOPS = listOf(0.5f, 1f, 2f)
private val THREE_STOPS_RANGE = 0.5f..8f
private val FOUR_STOPS = listOf(0.5f, 1f, 2f, 5f)
private val FOUR_STOPS_RANGE = 0.5f..10f

private enum class SampleCamera(
    val label: String,
    val valueRange: ClosedFloatingPointRange<Float>,
    val stops: List<Float>,
) {
    ThreeStops(
        label = "3 stops",
        valueRange = THREE_STOPS_RANGE,
        stops = THREE_STOPS,
    ),
    FourStops(
        label = "4 stops",
        valueRange = FOUR_STOPS_RANGE,
        stops = FOUR_STOPS,
    ),
}

@Preview(heightDp = 720)
@Composable
private fun ZoomControlSamplePreview() {
    ZoomControlSample()
}

@Composable
private fun ZoomControlSample() {
    val state = remember { ZoomControlSampleState() }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val valueLabel = rememberZoomFormat().format(value = state.zoom) + ZOOM_SUFFIX

    LaunchedEffect(state.isExpanded, state.changes, isPressed) {
        if (state.isExpanded && !isPressed) {
            delay(COLLAPSE_DELAY)
            state.collapse()
        }
    }

    CameraPreviewSample(
        status = "Zoom: $valueLabel",
        controls = {
            CameraPreviewControl(
                text = "Pinch in",
                onClick = state::pinchIn,
            )
            CameraPreviewControl(
                text = "Pinch out",
                onClick = state::pinchOut,
            )
            CameraPreviewControl(
                text = state.camera.label,
                onClick = state::nextCamera,
            )
        },
    ) {
        SampleControls(
            state = state,
            valueLabel = valueLabel,
            interactionSource = interactionSource,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun SampleControls(
    state: ZoomControlSampleState,
    valueLabel: String,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        AnimatedVisibility(
            visible = state.isExpanded,
            enter = POP_IN,
            exit = POP_OUT,
        ) {
            ValueChip(text = valueLabel)
        }
        key(state.camera) {
            ZoomControl(
                value = state.zoom,
                onValueChange = state::change,
                valueRange = state.camera.valueRange,
                stops = state.camera.stops,
                valueSuffix = ZOOM_SUFFIX,
                expanded = state.isExpanded,
                onExpand = state::expand,
                onStopClick = state::change,
                expandLabel = "Adjust zoom",
                interactionSource = interactionSource,
            )
        }
    }
}

@Stable
private class ZoomControlSampleState {

    var camera by mutableStateOf(SampleCamera.ThreeStops)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set
    var isExpanded by mutableStateOf(false)
        private set
    var changes by mutableIntStateOf(0)
        private set

    fun change(value: Float) {
        zoom = value
        changes += 1
    }

    fun expand() {
        isExpanded = true
        changes += 1
    }

    fun collapse() {
        isExpanded = false
    }

    fun pinchIn() {
        pinch(factor = 1f / PINCH_FACTOR)
    }

    fun pinchOut() {
        pinch(factor = PINCH_FACTOR)
    }

    fun nextCamera() {
        val cameras = SampleCamera.entries

        camera = cameras[(camera.ordinal + 1) % cameras.size]
        zoom = 1f
        isExpanded = false
    }

    private fun pinch(factor: Float) {
        val range = camera.valueRange

        change(value = (zoom * factor).coerceIn(range.start, range.endInclusive))
        expand()
    }
}
