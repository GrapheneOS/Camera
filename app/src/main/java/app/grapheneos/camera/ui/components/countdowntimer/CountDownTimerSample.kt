package app.grapheneos.camera.ui.components.countdowntimer

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.CaptureButton
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonCore
import app.grapheneos.camera.ui.components.progress.model.RingProgress
import app.grapheneos.camera.ui.core.CameraPreviewControl
import app.grapheneos.camera.ui.core.CameraPreviewSample
import app.grapheneos.camera.ui.core.PREVIEW_CLOSE_ICON
import app.grapheneos.camera.ui.core.PREVIEW_TIMER_ICON
import app.grapheneos.camera.ui.core.PreviewCountdown

private val TIMER_OPTIONS = listOf(3, 5, 10)

@Preview(heightDp = 720)
@Composable
private fun CountDownTimerSamplePreview() {
    CountDownTimerSample()
}

@Composable
private fun CountDownTimerSample() {
    val state = remember { CountDownTimerSampleState() }

    state.countdown.Effect(onFinished = state::capture)

    CameraPreviewSample(
        status = "Captured ${state.captures}",
        controls = {
            CameraPreviewControl(
                text = "Timer ${state.countdown.seconds}s",
                onClick = state::nextDuration,
            )
        },
    ) {
        if (state.countdown.isRunning) {
            CountDownTimer(
                value = state.countdown.secondsLeft,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        CaptureButton(
            onClick = state::click,
            core = state.core,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            progress = state.progress,
            icon = state.icon,
        )
    }
}

@Stable
private class CountDownTimerSampleState {

    val countdown = PreviewCountdown(initialSeconds = TIMER_OPTIONS.first())

    var captures by mutableIntStateOf(0)
        private set

    val core: CaptureButtonCore
        get() {
            return when {
                countdown.isRunning -> CaptureButtonCore.None
                else -> CaptureButtonCore.Disc
            }
        }

    val icon: ImageVector
        get() {
            return when {
                countdown.isRunning -> PREVIEW_CLOSE_ICON
                else -> PREVIEW_TIMER_ICON
            }
        }

    val progress: RingProgress
        get() {
            return when {
                countdown.isRunning -> RingProgress.Segmented(
                    segments = countdown.seconds,
                    fraction = countdown::remainingFraction,
                )

                else -> RingProgress.None
            }
        }

    fun click() {
        when {
            countdown.isRunning -> countdown.cancel()
            else -> countdown.start()
        }
    }

    fun nextDuration() {
        val next = (TIMER_OPTIONS.indexOf(countdown.seconds) + 1) % TIMER_OPTIONS.size

        countdown.seconds = TIMER_OPTIONS[next]
        countdown.cancel()
    }

    fun capture() {
        captures += 1
    }
}
