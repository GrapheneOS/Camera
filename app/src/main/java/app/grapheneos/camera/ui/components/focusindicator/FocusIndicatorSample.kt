package app.grapheneos.camera.ui.components.focusindicator

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.focusindicator.model.FocusIndicatorAppearance
import app.grapheneos.camera.ui.components.motion.ProvideContentRotation
import app.grapheneos.camera.ui.core.CameraPreviewSample
import app.grapheneos.camera.ui.core.PREVIEW_LOCK_ICON
import app.grapheneos.camera.ui.core.PreviewRotation
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private val FOCUS_DURATION = 600.milliseconds

@Preview(heightDp = 720)
@Composable
private fun FocusIndicatorSamplePreview() {
    FocusIndicatorSample()
}

@Composable
private fun FocusIndicatorSample() {
    val state = remember { FocusIndicatorSampleState() }
    val density = LocalDensity.current
    val ringRadius = with(density) { FOCUS_RING_DIAMETER.toPx() / 2 }
    val indicatorHalf = with(density) { FOCUS_INDICATOR_SIZE.toPx() / 2 }

    LaunchedEffect(state.focusRequest) {
        delay(FOCUS_DURATION)
        state.finishFocus()
    }

    CameraPreviewSample(
        status = state.status,
        controls = {
            state.rotation.Control()
        },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(state) {
                    val keepInside = { point: Offset ->
                        Offset(
                            x = point.x.coerceIn(indicatorHalf, size.width - indicatorHalf),
                            y = point.y.coerceIn(indicatorHalf, size.height - indicatorHalf),
                        )
                    }

                    detectTapGestures(
                        onPress = { point ->
                            state.press(
                                point = point,
                                center = keepInside(point),
                                ringRadius = ringRadius,
                            )
                        },
                        onTap = { state.tap() },
                        onLongPress = { state.lock() },
                    )
                },
        )
        ProvideContentRotation(degrees = state.rotation.degrees) {
            key(state.focusRequest) {
                FocusIndicator(
                    visible = state.isVisible,
                    appearance = state.appearance,
                    lockIcon = PREVIEW_LOCK_ICON,
                    modifier = Modifier.centerAt(point = state.point),
                )
            }
        }
    }
}

private fun Modifier.centerAt(point: Offset): Modifier {
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))

        layout(width = placeable.width, height = placeable.height) {
            placeable.place(
                x = (point.x - placeable.width / 2f).roundToInt(),
                y = (point.y - placeable.height / 2f).roundToInt(),
            )
        }
    }
}

@Stable
private class FocusIndicatorSampleState {

    val rotation = PreviewRotation()

    var point by mutableStateOf(Offset.Zero)
        private set
    var isVisible by mutableStateOf(false)
        private set
    var appearance by mutableStateOf(FocusIndicatorAppearance.Focusing)
        private set
    var focusRequest by mutableIntStateOf(0)
        private set

    private var isPressOnRing = false

    val status: String
        get() {
            return when {
                !isVisible -> "Tap to focus, hold to lock"
                else -> appearance.name
            }
        }

    fun press(
        point: Offset,
        center: Offset,
        ringRadius: Float,
    ) {
        isPressOnRing = isVisible &&
            (point - this.point).getDistanceSquared() <= ringRadius * ringRadius
        if (!isPressOnRing) {
            show(point = center)
        }
    }

    fun tap() {
        if (isPressOnRing) {
            isVisible = false
        }
    }

    fun lock() {
        appearance = FocusIndicatorAppearance.Locked
    }

    fun finishFocus() {
        if (appearance == FocusIndicatorAppearance.Focusing) {
            appearance = FocusIndicatorAppearance.Focused
        }
    }

    private fun show(point: Offset) {
        this.point = point
        appearance = FocusIndicatorAppearance.Focusing
        isVisible = true
        focusRequest += 1
    }
}
