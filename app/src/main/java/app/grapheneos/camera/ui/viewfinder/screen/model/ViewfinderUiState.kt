package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.camera.core.AspectRatio
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.GridType

data class ViewfinderUiState(
    val qrOverlayVisible: Boolean = false,
    val qrScanTogglesVisible: Boolean = false,
    val thirdOptionVisible: Boolean = true,
    val cancelButtonVisible: Boolean = true,
    val capturedPreviewVisible: Boolean = false,
    val micMutedIconVisible: Boolean = false,
    @DrawableRes val captureButtonBackground: Int = R.drawable.cbutton_bg,
    @DrawableRes val captureButtonIcon: Int = R.drawable.camera_shutter,
    @StringRes val captureButtonDescription: Int = R.string.capture,
    @DrawableRes val flipCameraIcon: Int = R.drawable.flip_camera,
    @StringRes val flipCameraDescription: Int = R.string.flip_camera,
    val selfTimerBadge: String = "",
    val selfTimerBadgeVisible: Boolean = false,
    val gridType: GridType = GridType.NONE,
    val mode: CameraMode = CameraMode.CAMERA,
    val aspectRatio: Int = AspectRatio.RATIO_4_3,
    val isQrMode: Boolean = false,
    val isVideoMode: Boolean = false,
    val inPhotoMode: Boolean = true,
    val scanAllCodes: Boolean = false,
    val focusTimeoutSeconds: Long = 0,
    val gyroscopeSuggestionsVisible: Boolean = false,
    val settingsSheet: SettingsSheetUiState = SettingsSheetUiState(),
    val capture: CaptureUiState = CaptureUiState(),
)
