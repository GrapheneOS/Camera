package app.grapheneos.camera.ui.components.countdowntimer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.CaptureButton
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonCore
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonProgress
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_CLOSE_ICON
import app.grapheneos.camera.ui.core.PREVIEW_TIMER_ICON
import app.grapheneos.camera.ui.core.cameraColors
import kotlin.math.floor

private const val SECOND_MILLIS = 1_000
private const val DRAIN_START = 0.5f
private const val VIEWFINDER_ASPECT_RATIO = 3f / 4f

private val TIMER_OPTIONS = listOf(3, 5, 10)

@Preview(heightDp = 720)
@Composable
private fun CountDownTimerSamplePreview() {
    CameraPreviewColumn {
        CountDownTimerSample()
    }
}

@Composable
private fun CountDownTimerSample() {
    val state = remember { CountDownTimerSampleState() }

    LaunchedEffect(state.countdowns, state.isCountingDown) {
        state.runCountdown()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(text = "Captured ${state.captures}")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio = VIEWFINDER_ASPECT_RATIO)
                .background(
                    color = MaterialTheme.cameraColors.overlayScrim,
                    shape = MaterialTheme.shapes.large,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isCountingDown) {
                CountDownTimer(value = state.value)
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
        TextButton(onClick = state::nextDuration) {
            Text(text = "Timer ${state.seconds}s")
        }
    }
}

@Stable
private class CountDownTimerSampleState {

    var seconds by mutableIntStateOf(TIMER_OPTIONS.first())
        private set
    var captures by mutableIntStateOf(0)
        private set
    var isCountingDown by mutableStateOf(false)
        private set
    var countdowns by mutableIntStateOf(0)
        private set

    private val elapsedSeconds = Animatable(initialValue = 0f)

    val value: Int by derivedStateOf {
        (seconds - floor(elapsedSeconds.value).toInt()).coerceAtLeast(1)
    }

    val core: CaptureButtonCore
        get() {
            return when {
                isCountingDown -> CaptureButtonCore.None
                else -> CaptureButtonCore.Disc
            }
        }

    val icon: ImageVector
        get() {
            return when {
                isCountingDown -> PREVIEW_CLOSE_ICON
                else -> PREVIEW_TIMER_ICON
            }
        }

    val progress: CaptureButtonProgress
        get() {
            return when {
                isCountingDown -> CaptureButtonProgress.Segmented(
                    segments = seconds,
                    fraction = ::remainingFraction,
                )

                else -> CaptureButtonProgress.None
            }
        }

    fun click() {
        when {
            isCountingDown -> isCountingDown = false
            else -> {
                isCountingDown = true
                countdowns += 1
            }
        }
    }

    fun nextDuration() {
        val next = (TIMER_OPTIONS.indexOf(seconds) + 1) % TIMER_OPTIONS.size

        seconds = TIMER_OPTIONS[next]
        isCountingDown = false
    }

    suspend fun runCountdown() {
        if (!isCountingDown) return

        elapsedSeconds.snapTo(targetValue = 0f)
        elapsedSeconds.animateTo(
            targetValue = seconds.toFloat(),
            animationSpec = tween(
                durationMillis = seconds * SECOND_MILLIS,
                easing = LinearEasing,
            ),
        )
        if (isCountingDown) {
            isCountingDown = false
            captures += 1
        }
    }

    private fun remainingFraction(): Float {
        val elapsed = elapsedSeconds.value
        val second = floor(elapsed)
        val drainProgress = ((elapsed - second - DRAIN_START) / (1f - DRAIN_START)).coerceIn(0f, 1f)
        val drain = FastOutSlowInEasing.transform(drainProgress)

        return (seconds - second - drain) / seconds
    }
}
