package app.grapheneos.camera.ui.components.levelindicator

import android.icu.text.NumberFormat
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.adjustmentbar.AdjustmentBar
import app.grapheneos.camera.ui.components.motion.ProvideContentRotation
import app.grapheneos.camera.ui.components.motion.rotateLayout
import app.grapheneos.camera.ui.core.CameraPreviewSample
import app.grapheneos.camera.ui.core.PreviewRotation

private val ROLL_RANGE = -30f..30f
private val PITCH_RANGE = -90f..90f

private val BAR_PADDING = 16.dp

private const val ROLL_STEPS = 59
private const val PITCH_STEPS = 59
private const val MAJOR_TICK_INTERVAL = 5
private const val INITIAL_ROLL = 22f
private const val VERTICAL_TURN = -90f

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

    CameraPreviewSample(
        status = "Roll ${degrees.format(state.roll)}°, pitch ${degrees.format(state.pitch)}°",
        controls = {
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
        SampleBars(state = state)
    }
}

@Composable
private fun BoxScope.SampleBars(state: LevelIndicatorSampleState) {
    AdjustmentBar(
        value = state.roll,
        onValueChange = state::changeRoll,
        valueRange = ROLL_RANGE,
        steps = ROLL_STEPS,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(all = BAR_PADDING)
            .semantics { contentDescription = "Roll" },
        majorTickInterval = MAJOR_TICK_INTERVAL,
    )
    AdjustmentBar(
        value = state.pitch,
        onValueChange = state::changePitch,
        valueRange = PITCH_RANGE,
        steps = PITCH_STEPS,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(all = BAR_PADDING)
            .rotateLayout { VERTICAL_TURN }
            .semantics { contentDescription = "Pitch" },
        majorTickInterval = MAJOR_TICK_INTERVAL,
    )
}

@Stable
private class LevelIndicatorSampleState {

    val rotation = PreviewRotation()

    var roll by mutableFloatStateOf(INITIAL_ROLL)
        private set
    var pitch by mutableFloatStateOf(0f)
        private set

    fun changeRoll(value: Float) {
        roll = value
    }

    fun changePitch(value: Float) {
        pitch = value
    }
}
