package app.grapheneos.camera.ui.core

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.math.floor

@Stable
internal class PreviewCountdown(
    initialSeconds: Int,
) {

    var seconds by mutableIntStateOf(initialSeconds)
    var isRunning by mutableStateOf(false)
        private set

    private var runs by mutableIntStateOf(0)
    private val elapsedSeconds = Animatable(initialValue = 0f)

    val secondsLeft: Int by derivedStateOf {
        (seconds - floor(elapsedSeconds.value).toInt()).coerceAtLeast(1)
    }

    fun start() {
        isRunning = true
        runs += 1
    }

    fun cancel() {
        isRunning = false
    }

    fun remainingFraction(): Float {
        val elapsed = elapsedSeconds.value
        val second = floor(elapsed)
        val drainProgress = ((elapsed - second - DRAIN_START) / (1f - DRAIN_START)).coerceIn(0f, 1f)
        val drain = FastOutSlowInEasing.transform(drainProgress)

        return (seconds - second - drain) / seconds
    }

    @Composable
    fun Effect(onFinished: () -> Unit) {
        val currentOnFinished by rememberUpdatedState(onFinished)

        LaunchedEffect(this, runs, isRunning) {
            if (run()) {
                currentOnFinished()
            }
        }
    }

    private suspend fun run(): Boolean {
        elapsedSeconds.snapTo(targetValue = 0f)

        if (!isRunning) return false

        elapsedSeconds.animateTo(
            targetValue = seconds.toFloat(),
            animationSpec = tween(
                durationMillis = seconds * SECOND_MILLIS,
                easing = LinearEasing,
            ),
        )
        val isFinished = isRunning
        isRunning = false

        return isFinished
    }

    private companion object {
        private const val SECOND_MILLIS = 1_000
        private const val DRAIN_START = 0.5f
    }
}
