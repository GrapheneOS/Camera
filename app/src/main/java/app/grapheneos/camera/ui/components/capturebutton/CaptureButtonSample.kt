package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonCore
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonDirection
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonHoldEnd
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonProgress
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTone
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_CLOSE_ICON
import app.grapheneos.camera.ui.core.PREVIEW_LOCK_ICON
import app.grapheneos.camera.ui.core.PREVIEW_TIMER_ICON
import java.util.Locale

private const val TIMER_SECONDS = 3
private const val SECOND_MILLIS = 1_000
private const val SEGMENT_DRAIN_MILLIS = 500
private const val ZOOM_DRAG_PX = 400f
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 10f
private const val QUARTER_TURN = 90f
private const val FULL_TURN = 360f

private val SAMPLE_AREA_HEIGHT = 200.dp
private val SAMPLE_LOCK = CaptureButtonTarget(
    direction = CaptureButtonDirection.Start,
    distance = 120.dp,
    icon = PREVIEW_LOCK_ICON,
    accessibilityLabel = "Lock recording",
)

private enum class SampleRecording {
    Idle,
    Held,
    Locked,
}

@Preview(heightDp = 400)
@Composable
private fun CaptureButtonSamplePreview() {
    CameraPreviewColumn {
        CaptureButtonSample()
    }
}

@Composable
private fun CaptureButtonSample() {
    val state = remember { CaptureButtonSampleState() }

    LaunchedEffect(state.countdowns, state.isCountingDown) {
        state.runCountdown()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(text = "Zoom ${"%.1f".format(Locale.ROOT, state.zoom)}×, ${state.recording}")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SAMPLE_AREA_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            SampleCaptureButton(state = state)
        }
        SampleControls(state = state)
    }
}

@Composable
private fun SampleCaptureButton(state: CaptureButtonSampleState) {
    CaptureButton(
        onClick = state::click,
        core = state.core,
        tone = state.tone,
        progress = state.progress,
        trigger = state.trigger,
        icon = state.icon,
        iconRotationDegrees = state.rotation,
        onHoldStart = state::startHold.takeIf { state.canHold },
        onHoldDrag = state::zoomBy,
        onHoldEnd = state::endHold,
        holdTargets = listOf(SAMPLE_LOCK),
    )
}

@Composable
private fun SampleControls(state: CaptureButtonSampleState) {
    Row {
        TextButton(onClick = { state.isVideoMode = !state.isVideoMode }) {
            Text(
                text = when {
                    state.isVideoMode -> "Video"
                    else -> "Photo"
                },
            )
        }
        TextButton(onClick = { state.isTimerOn = !state.isTimerOn }) {
            Text(
                text = when {
                    state.isTimerOn -> "Timer ${TIMER_SECONDS}s"
                    else -> "No timer"
                },
            )
        }
        TextButton(onClick = state::rotate) {
            Text(text = "Rotate ${state.rotation.toInt()}°")
        }
    }
}

@Stable
private class CaptureButtonSampleState {

    var isVideoMode by mutableStateOf(false)
    var isTimerOn by mutableStateOf(false)
    var isCountingDown by mutableStateOf(false)
        private set
    var countdowns by mutableIntStateOf(0)
        private set
    var recording by mutableStateOf(SampleRecording.Idle)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set
    var rotation by mutableFloatStateOf(0f)
        private set

    private val countdown = Animatable(initialValue = 1f)

    val canHold: Boolean
        get() {
            return !isVideoMode && !isCountingDown && recording == SampleRecording.Idle
        }

    val core: CaptureButtonCore
        get() {
            return when {
                isCountingDown -> CaptureButtonCore.None
                recording == SampleRecording.Locked -> CaptureButtonCore.Square
                recording == SampleRecording.Held || isVideoMode -> CaptureButtonCore.Dot
                else -> CaptureButtonCore.Disc
            }
        }

    val tone: CaptureButtonTone
        get() {
            return when (recording) {
                SampleRecording.Idle -> CaptureButtonTone.Neutral
                else -> CaptureButtonTone.Recording
            }
        }

    val progress: CaptureButtonProgress
        get() {
            return when {
                isCountingDown -> CaptureButtonProgress.Segmented(
                    segments = TIMER_SECONDS,
                    fraction = { countdown.value },
                )

                else -> CaptureButtonProgress.None
            }
        }

    val trigger: CaptureButtonTrigger
        get() {
            return when {
                isVideoMode -> CaptureButtonTrigger.Press
                else -> CaptureButtonTrigger.Release
            }
        }

    val icon: ImageVector?
        get() {
            return when {
                isCountingDown -> PREVIEW_CLOSE_ICON
                isTimerOn && recording == SampleRecording.Idle -> PREVIEW_TIMER_ICON
                else -> null
            }
        }

    fun click() {
        when {
            isCountingDown -> isCountingDown = false
            recording != SampleRecording.Idle -> recording = SampleRecording.Idle
            isTimerOn -> startCountdown()
            isVideoMode -> recording = SampleRecording.Locked
        }
    }

    private fun startCountdown() {
        isCountingDown = true
        countdowns += 1
    }

    fun startHold() {
        recording = SampleRecording.Held
    }

    fun endHold(end: CaptureButtonHoldEnd) {
        recording = when (end) {
            is CaptureButtonHoldEnd.Committed -> SampleRecording.Locked
            else -> SampleRecording.Idle
        }
    }

    fun zoomBy(delta: Offset) {
        zoom = (zoom * (1f - delta.y / ZOOM_DRAG_PX)).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun rotate() {
        rotation = (rotation + QUARTER_TURN) % FULL_TURN
    }

    suspend fun runCountdown() {
        if (!isCountingDown) return

        countdown.snapTo(targetValue = 1f)
        countdown.animateTo(
            targetValue = 0f,
            animationSpec = countdownSpec(),
        )
        if (isCountingDown) {
            isCountingDown = false
            if (isVideoMode) {
                recording = SampleRecording.Locked
            }
        }
    }

    private fun countdownSpec(): KeyframesSpec<Float> {
        return keyframes {
            durationMillis = TIMER_SECONDS * SECOND_MILLIS
            repeat(TIMER_SECONDS) { second ->
                val drainStart = (second + 1) * SECOND_MILLIS - SEGMENT_DRAIN_MILLIS

                remainingFraction(elapsedSeconds = second) at drainStart using FastOutSlowInEasing
                remainingFraction(elapsedSeconds = second + 1) at (second + 1) * SECOND_MILLIS
            }
        }
    }

    private fun remainingFraction(elapsedSeconds: Int): Float {
        return (TIMER_SECONDS - elapsedSeconds).toFloat() / TIMER_SECONDS
    }
}
