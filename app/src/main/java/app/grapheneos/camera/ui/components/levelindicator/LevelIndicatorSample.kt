package app.grapheneos.camera.ui.components.levelindicator

import android.icu.text.NumberFormat
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import app.grapheneos.camera.ui.components.motion.ProvideContentRotation
import app.grapheneos.camera.ui.core.CameraPreviewControl
import app.grapheneos.camera.ui.core.CameraPreviewSample
import app.grapheneos.camera.ui.core.PreviewRotation
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private val SAMPLE_ROLLS = listOf(22f, 6f, 1f, 0.3f, 0f)
private val SAMPLE_PITCHES = listOf(0f, 0.6f, 10f, -10f)
private val SHAKE_INTERVAL = 50.milliseconds

@Preview(heightDp = 720)
@Composable
private fun LevelIndicatorSamplePreview() {
    LevelIndicatorSample()
}

@Composable
private fun LevelIndicatorSample() {
    val state = remember { LevelIndicatorSampleState() }
    val locale = LocalConfiguration.current.locales[0]
    val degrees = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    }

    LaunchedEffect(state.isShaking) {
        while (state.isShaking) {
            state.shake()
            delay(SHAKE_INTERVAL)
        }
        state.steady()
    }

    CameraPreviewSample(
        status = state.status,
        controls = {
            CameraPreviewControl(
                text = "Roll ${degrees.format(state.presetRoll)}°",
                onClick = state::nextRoll,
            )
            CameraPreviewControl(
                text = "Pitch ${degrees.format(state.presetPitch)}°",
                onClick = state::nextPitch,
            )
            CameraPreviewControl(
                text = state.shakeLabel,
                onClick = state::switchShake,
            )
            state.rotation.Control()
        },
    ) {
        ProvideContentRotation(degrees = state.rotation.degrees) {
            LevelIndicator(
                roll = state::roll,
                pitch = state::pitch,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Stable
private class LevelIndicatorSampleState {

    val rotation = PreviewRotation()

    var isShaking by mutableStateOf(false)
        private set

    private var rollIndex by mutableIntStateOf(0)
    private var pitchIndex by mutableIntStateOf(0)
    private var rollNoise by mutableFloatStateOf(0f)
    private var pitchNoise by mutableFloatStateOf(0f)

    val roll: Float
        get() {
            return presetRoll + rollNoise
        }

    val pitch: Float
        get() {
            return presetPitch + pitchNoise
        }

    val status: String
        get() {
            val zone = LevelZone.of(
                roll = presetRoll,
                pitch = presetPitch,
            )

            return when (zone) {
                LevelZone.Level -> "Level: the lines meet, a tick plays"
                else -> "Not level: turn and tilt until the lines meet"
            }
        }

    val presetRoll: Float
        get() {
            return SAMPLE_ROLLS[rollIndex]
        }

    val presetPitch: Float
        get() {
            return SAMPLE_PITCHES[pitchIndex]
        }

    val shakeLabel: String
        get() {
            return when {
                isShaking -> "Shaking"
                else -> "Steady"
            }
        }

    fun nextRoll() {
        rollIndex = (rollIndex + 1) % SAMPLE_ROLLS.size
    }

    fun nextPitch() {
        pitchIndex = (pitchIndex + 1) % SAMPLE_PITCHES.size
    }

    fun switchShake() {
        isShaking = !isShaking
    }

    fun shake() {
        rollNoise = noise()
        pitchNoise = noise()
    }

    fun steady() {
        rollNoise = 0f
        pitchNoise = 0f
    }

    private fun noise(): Float {
        return (Random.nextFloat() * 2 - 1) * SHAKE_DEGREES
    }

    private companion object {
        private const val SHAKE_DEGREES = 0.8f
    }
}
