package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.GridType

data class ViewfinderUiState(
    val qrOverlayVisible: Boolean = false,
    val qrScanTogglesVisible: Boolean = false,
    val thirdOptionVisible: Boolean = true,
    val cancelButtonVisible: Boolean = true,
    val capturedPreviewVisible: Boolean = false,
    val qrResultVisible: Boolean = false,
    val micMutedIconVisible: Boolean = false,
    val captureButtonEnabled: Boolean = true,
    val isRecordingActive: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val isRecordingMuted: Boolean = false,
    val muteToggleVisible: Boolean = false,
    val thumbnailLoaderVisible: Boolean = false,
    @DrawableRes val captureButtonBackground: Int = R.drawable.cbutton_bg,
    @DrawableRes val captureButtonIcon: Int = R.drawable.camera_shutter,
    @StringRes val captureButtonDescription: Int = R.string.capture,
    @DrawableRes val flipCameraIcon: Int = R.drawable.flip_camera,
    @StringRes val flipCameraDescription: Int = R.string.flip_camera,
    val selfTimerBadge: String = "",
    val selfTimerBadgeVisible: Boolean = false,
    val selfTimerCountdownVisible: Boolean = false,
    val selfTimerCancelVisible: Boolean = false,
    val gridType: GridType = GridType.NONE,
    val mode: CameraMode = CameraMode.CAMERA,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val isQrMode: Boolean = false,
    val isVideoMode: Boolean = false,
    val inPhotoMode: Boolean = true,
    val scanAllCodes: Boolean = false,
    val gyroscopeSuggestionsVisible: Boolean = false,
    val availableModes: Set<CameraMode> = emptySet(),
    val zslSupported: Boolean = false,
    val sensorOrientationDegrees: Int? = null,
    val zoom: ZoomUiState = ZoomUiState(),
    val exposure: ExposureUiState? = null,
    val settingsSheet: SettingsSheetUiState = SettingsSheetUiState(),
    val capture: CaptureUiState = CaptureUiState(),
)
