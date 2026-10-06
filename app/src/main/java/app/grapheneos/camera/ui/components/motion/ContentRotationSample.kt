package app.grapheneos.camera.ui.components.motion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.CaptureButton
import app.grapheneos.camera.ui.components.lensswitchbutton.LensSwitchButton
import app.grapheneos.camera.ui.components.overlayiconbutton.OverlayIconButton
import app.grapheneos.camera.ui.components.segmentedicontoggle.SegmentedIconToggle
import app.grapheneos.camera.ui.components.segmentedicontoggle.model.SegmentedIconToggleOption
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterCore
import app.grapheneos.camera.ui.components.thumbnailbutton.ThumbnailButton
import app.grapheneos.camera.ui.components.zoom.ZoomStops
import app.grapheneos.camera.ui.core.CameraPreviewControl
import app.grapheneos.camera.ui.core.CameraPreviewSample
import app.grapheneos.camera.ui.core.PREVIEW_LENS_SWITCH_ICON
import app.grapheneos.camera.ui.core.PREVIEW_PHOTO_CAMERA_ICON
import app.grapheneos.camera.ui.core.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.PREVIEW_TIMER_ICON
import app.grapheneos.camera.ui.core.PREVIEW_VIDEOCAM_ICON
import app.grapheneos.camera.ui.core.PREVIEW_WARM_ICON
import app.grapheneos.camera.ui.core.previewShot

private val SAMPLE_STOPS = listOf(0.5f, 1f, 2f)
private val SAMPLE_MODES = listOf(
    SegmentedIconToggleOption(
        icon = PREVIEW_PHOTO_CAMERA_ICON,
        contentDescription = "Photo",
    ),
    SegmentedIconToggleOption(
        icon = PREVIEW_VIDEOCAM_ICON,
        contentDescription = "Video",
    ),
)

@Preview(heightDp = 720)
@Composable
private fun ContentRotationSamplePreview() {
    ContentRotationSample()
}

@Composable
private fun ContentRotationSample() {
    val state = remember { ContentRotationSampleState() }

    CameraPreviewSample(
        status = "Rotation ${state.degrees.toInt()}°",
        controls = {
            CameraPreviewControl(
                text = "Rotate",
                onClick = state::rotate,
            )
        },
    ) {
        ProvideContentRotation(degrees = state.degrees) {
            OverlayIconButton(
                onClick = {},
                icon = PREVIEW_TIMER_ICON,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(all = 16.dp),
            )
            SampleControls(
                state = state,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(all = 16.dp),
            )
        }
    }
}

@Composable
private fun SampleControls(
    state: ContentRotationSampleState,
    modifier: Modifier = Modifier,
) {
    val shot = remember { previewShot(top = PREVIEW_WARM_ICON, bottom = PREVIEW_SCENE) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZoomStops(
            value = state.zoom,
            stops = SAMPLE_STOPS,
            valueSuffix = "×",
            onStopClick = state::zoomTo,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThumbnailButton(
                image = shot,
                onClick = {},
            )
            CaptureButton(
                onClick = {},
                core = ShutterCore.Disc,
            )
            LensSwitchButton(
                flipped = state.isFront,
                onClick = state::flip,
                icon = PREVIEW_LENS_SWITCH_ICON,
            )
        }
        SegmentedIconToggle(
            options = SAMPLE_MODES,
            selectedIndex = state.modeIndex,
            onOptionSelected = state::selectMode,
        )
    }
}

@Stable
private class ContentRotationSampleState {

    var degrees by mutableFloatStateOf(0f)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set
    var isFront by mutableStateOf(false)
        private set
    var modeIndex by mutableIntStateOf(0)
        private set

    fun rotate() {
        degrees = (degrees + QUARTER_TURN) % FULL_TURN
    }

    fun zoomTo(value: Float) {
        zoom = value
    }

    fun flip() {
        isFront = !isFront
    }

    fun selectMode(index: Int) {
        modeIndex = index
    }

    private companion object {
        private const val QUARTER_TURN = 90f
        private const val FULL_TURN = 360f
    }
}
