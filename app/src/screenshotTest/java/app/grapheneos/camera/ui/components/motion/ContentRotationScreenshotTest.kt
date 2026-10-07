package app.grapheneos.camera.ui.components.motion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.adjustmentbar.AdjustmentBar
import app.grapheneos.camera.ui.components.capturebutton.CaptureButton
import app.grapheneos.camera.ui.components.countdowntimer.CountDownTimer
import app.grapheneos.camera.ui.components.overlayiconbutton.OverlayIconButton
import app.grapheneos.camera.ui.components.segmentedicontoggle.SegmentedIconToggle
import app.grapheneos.camera.ui.components.segmentedicontoggle.model.SegmentedIconToggleOption
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterCore
import app.grapheneos.camera.ui.components.squarebutton.SquareMarkButton
import app.grapheneos.camera.ui.components.squarebutton.model.SquareButtonMark
import app.grapheneos.camera.ui.components.thumbnailbutton.ThumbnailButton
import app.grapheneos.camera.ui.components.zoom.ZoomStops
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_BRIGHTNESS_HIGH_ICON
import app.grapheneos.camera.ui.core.PREVIEW_BRIGHTNESS_LOW_ICON
import app.grapheneos.camera.ui.core.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.PREVIEW_PAUSE_ICON
import app.grapheneos.camera.ui.core.PREVIEW_PHOTO_CAMERA_ICON
import app.grapheneos.camera.ui.core.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.PREVIEW_TIMER_ICON
import app.grapheneos.camera.ui.core.PREVIEW_VIDEOCAM_ICON
import app.grapheneos.camera.ui.core.previewShot
import com.android.tools.screenshot.PreviewTest

private val MODES = listOf(
    SegmentedIconToggleOption(
        icon = PREVIEW_PHOTO_CAMERA_ICON,
        contentDescription = "Photo",
    ),
    SegmentedIconToggleOption(
        icon = PREVIEW_VIDEOCAM_ICON,
        contentDescription = "Video",
    ),
)

@PreviewTest
@Preview
@Composable
private fun ContentRotationQuarterTurn() {
    RotatedComponents(degrees = 90f)
}

@PreviewTest
@Preview
@Composable
private fun ContentRotationThreeQuarterTurn() {
    RotatedComponents(degrees = 270f)
}

@Composable
private fun RotatedComponents(degrees: Float) {
    CameraPreviewColumn(darkTheme = true) {
        ProvideContentRotation(degrees = degrees) {
            Column(
                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RotatedButtons()
                RotatedSelectors()
                AdjustmentBar(
                    value = 0f,
                    onValueChange = {},
                    valueRange = -12f..12f,
                    steps = 23,
                    startIcon = PREVIEW_BRIGHTNESS_LOW_ICON,
                    endIcon = PREVIEW_BRIGHTNESS_HIGH_ICON,
                )
            }
        }
    }
}

@Composable
private fun RotatedButtons() {
    val shot = remember {
        previewShot(
            top = PREVIEW_COOL_ICON,
            bottom = PREVIEW_SCENE,
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThumbnailButton(
            image = shot,
            onClick = {},
        )
        CaptureButton(
            onClick = {},
            core = ShutterCore.None,
            icon = PREVIEW_PAUSE_ICON,
        )
        SquareMarkButton(
            onClick = {},
            mark = SquareButtonMark.Icon(
                icon = PREVIEW_PAUSE_ICON,
            ),
        )
        OverlayIconButton(
            onClick = {},
            icon = PREVIEW_TIMER_ICON,
        )
    }
}

@Composable
private fun RotatedSelectors() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZoomStops(
            value = 1f,
            stops = listOf(0.5f, 1f, 2f),
            valueSuffix = "×",
            onStopClick = {},
        )
        SegmentedIconToggle(
            options = MODES,
            selectedIndex = 0,
            onOptionSelected = {},
        )
        CountDownTimer(value = 3)
    }
}
