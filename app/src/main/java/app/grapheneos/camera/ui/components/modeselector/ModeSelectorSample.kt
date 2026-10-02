package app.grapheneos.camera.ui.components.modeselector

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewControl
import app.grapheneos.camera.ui.core.CameraPreviewSample
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private val SWITCH_DURATION = 1.seconds

private val SAMPLE_MODES = listOf(
    "Long Exposure",
    "Portrait",
    "Photo",
    "Night Sight",
    "Panorama",
    "Video",
)

@Preview(heightDp = 720)
@Composable
private fun ModeSelectorSamplePreview() {
    ModeSelectorSample()
}

@Composable
private fun ModeSelectorSample() {
    val state = remember { ModeSelectorSampleState() }

    LaunchedEffect(state.isSwitching) {
        if (state.isSwitching) {
            delay(SWITCH_DURATION)
            state.finishSwitch()
        }
    }

    CameraPreviewSample(
        status = state.status,
        controls = {
            CameraPreviewControl(
                text = state.loadingLabel,
                onClick = state::toggleLoading,
            )
        },
    ) {
        ModeSelector(
            labels = SAMPLE_MODES,
            selectedIndex = state.modeIndex,
            onModeSelected = state::selectMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            switching = state.isSwitching,
        )
    }
}

@Stable
private class ModeSelectorSampleState {

    var modeIndex by mutableIntStateOf(PHOTO_INDEX)
        private set
    var isSwitching by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set

    val status: String
        get() {
            val mode = "Mode: ${SAMPLE_MODES[modeIndex]}"

            return when {
                isSwitching -> "$mode (loading)"
                else -> mode
            }
        }

    val loadingLabel: String
        get() {
            return when {
                isLoading -> "Loading on"
                else -> "Loading off"
            }
        }

    fun selectMode(index: Int) {
        modeIndex = index
        isSwitching = isLoading
    }

    fun finishSwitch() {
        isSwitching = false
    }

    fun toggleLoading() {
        isLoading = !isLoading
    }

    private companion object {
        private const val PHOTO_INDEX = 2
    }
}
