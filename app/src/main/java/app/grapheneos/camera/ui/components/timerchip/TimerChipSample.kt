package app.grapheneos.camera.ui.components.timerchip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.POP_IN
import app.grapheneos.camera.ui.components.motion.POP_OUT
import app.grapheneos.camera.ui.components.motion.ProvideContentRotation
import app.grapheneos.camera.ui.core.CameraPreviewControl
import app.grapheneos.camera.ui.core.CameraPreviewSample
import app.grapheneos.camera.ui.core.PreviewRotation
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private val TICK = 1.seconds

@Preview(heightDp = 720)
@Composable
private fun TimerChipSamplePreview() {
    TimerChipSample()
}

@Composable
private fun TimerChipSample() {
    val state = remember { TimerChipSampleState() }

    LaunchedEffect(state.isRunning) {
        while (state.isRunning) {
            delay(TICK)
            state.tick()
        }
    }

    CameraPreviewSample(
        status = state.status,
        controls = {
            CameraPreviewControl(
                text = state.recordLabel,
                onClick = state::toggleRecording,
            )
            CameraPreviewControl(
                text = state.pauseLabel,
                onClick = state::togglePause,
            )
            CameraPreviewControl(
                text = "+1 h",
                onClick = state::addHour,
            )
            state.rotation.Control()
        },
    ) {
        ProvideContentRotation(degrees = state.rotation.degrees) {
            AnimatedVisibility(
                visible = state.isRecording,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(all = 16.dp),
                enter = POP_IN,
                exit = POP_OUT,
            ) {
                TimerChip(
                    elapsed = state.elapsed,
                    label = "PAUSED".takeIf { state.isPaused },
                )
            }
        }
    }
}

@Stable
private class TimerChipSampleState {

    val rotation = PreviewRotation()

    var isRecording by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var elapsed by mutableStateOf(Duration.ZERO)
        private set

    val isRunning: Boolean
        get() {
            return isRecording && !isPaused
        }

    val status: String
        get() {
            return when {
                !isRecording -> "Stopped"
                isPaused -> "Paused"
                else -> "Recording"
            }
        }

    val recordLabel: String
        get() {
            return when {
                isRecording -> "Stop"
                else -> "Record"
            }
        }

    val pauseLabel: String
        get() {
            return when {
                isPaused -> "Resume"
                else -> "Pause"
            }
        }

    fun toggleRecording() {
        if (!isRecording) {
            elapsed = Duration.ZERO
        }
        isRecording = !isRecording
        isPaused = false
    }

    fun togglePause() {
        if (isRecording) {
            isPaused = !isPaused
        }
    }

    fun addHour() {
        elapsed += 1.hours
    }

    fun tick() {
        elapsed += TICK
    }
}
