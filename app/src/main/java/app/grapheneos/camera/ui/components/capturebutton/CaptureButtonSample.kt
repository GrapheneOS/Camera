package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonDirection
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonHoldEnd
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger
import app.grapheneos.camera.ui.components.motion.ProvideContentRotation
import app.grapheneos.camera.ui.components.progress.model.RingProgress
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterCore
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterTone
import app.grapheneos.camera.ui.core.CameraPreviewControl
import app.grapheneos.camera.ui.core.CameraPreviewSample
import app.grapheneos.camera.ui.core.PREVIEW_CLOSE_ICON
import app.grapheneos.camera.ui.core.PREVIEW_LOCK_ICON
import app.grapheneos.camera.ui.core.PREVIEW_TIMER_ICON
import app.grapheneos.camera.ui.core.PreviewCountdown
import app.grapheneos.camera.ui.core.PreviewRotation
import java.util.Locale

private const val TIMER_SECONDS = 3
private const val ZOOM_DRAG_PX = 400f
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 10f

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

@Preview(heightDp = 720)
@Composable
private fun CaptureButtonSamplePreview() {
    CaptureButtonSample()
}

@Composable
private fun CaptureButtonSample() {
    val state = remember { CaptureButtonSampleState() }

    state.countdown.Effect(onFinished = state::finishCountdown)

    CameraPreviewSample(
        status = "Zoom ${"%.1f".format(Locale.ROOT, state.zoom)}×, ${state.recording}",
        controls = {
            CameraPreviewControl(
                text = state.modeLabel,
                onClick = state::switchMode,
            )
            CameraPreviewControl(
                text = state.timerLabel,
                onClick = state::switchTimer,
            )
            state.rotation.Control()
        },
    ) {
        SampleCaptureButton(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun SampleCaptureButton(
    state: CaptureButtonSampleState,
    modifier: Modifier = Modifier,
) {
    ProvideContentRotation(degrees = state.rotation.degrees) {
        CaptureButton(
            onClick = state::click,
            core = state.core,
            modifier = modifier,
            tone = state.tone,
            progress = state.progress,
            trigger = state.trigger,
            icon = state.icon,
            onHoldStart = state::startHold.takeIf { state.canHold },
            onHoldDrag = state::zoomBy,
            onHoldEnd = state::endHold,
            holdTargets = listOf(SAMPLE_LOCK),
        )
    }
}

@Stable
private class CaptureButtonSampleState {

    val rotation = PreviewRotation()

    var isVideoMode by mutableStateOf(false)
        private set
    var isTimerOn by mutableStateOf(false)
        private set
    var recording by mutableStateOf(SampleRecording.Idle)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set

    val countdown = PreviewCountdown(initialSeconds = TIMER_SECONDS)

    private val isCountingDown: Boolean
        get() {
            return countdown.isRunning
        }

    val canHold: Boolean
        get() {
            return !isVideoMode && !isCountingDown && recording == SampleRecording.Idle
        }

    val core: ShutterCore
        get() {
            return when {
                isCountingDown -> ShutterCore.None
                recording == SampleRecording.Locked -> ShutterCore.Square
                recording == SampleRecording.Held || isVideoMode -> ShutterCore.Dot
                else -> ShutterCore.Disc
            }
        }

    val tone: ShutterTone
        get() {
            return when (recording) {
                SampleRecording.Idle -> ShutterTone.Neutral
                else -> ShutterTone.Recording
            }
        }

    val progress: RingProgress
        get() {
            return when {
                isCountingDown -> RingProgress.Segmented(
                    segments = TIMER_SECONDS,
                    fraction = countdown::remainingFraction,
                )

                else -> RingProgress.None
            }
        }

    val modeLabel: String
        get() {
            return when {
                isVideoMode -> "Video"
                else -> "Photo"
            }
        }

    val timerLabel: String
        get() {
            return when {
                isTimerOn -> "Timer ${TIMER_SECONDS}s"
                else -> "No timer"
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
            isCountingDown -> countdown.cancel()
            recording != SampleRecording.Idle -> recording = SampleRecording.Idle
            isTimerOn -> countdown.start()
            isVideoMode -> recording = SampleRecording.Locked
        }
    }

    fun switchMode() {
        isVideoMode = !isVideoMode
    }

    fun switchTimer() {
        isTimerOn = !isTimerOn
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

    fun finishCountdown() {
        if (isVideoMode) {
            recording = SampleRecording.Locked
        }
    }
}
