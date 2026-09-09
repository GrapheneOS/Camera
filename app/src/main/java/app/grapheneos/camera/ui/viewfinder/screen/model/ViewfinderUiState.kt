package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.grapheneos.camera.R

data class ViewfinderUiState(
    val qrOverlayVisible: Boolean = false,
    val qrScanTogglesVisible: Boolean = false,
    val thirdOptionVisible: Boolean = true,
    val cancelButtonVisible: Boolean = true,
    val micMutedIconVisible: Boolean = false,
    @DrawableRes val captureButtonBackground: Int = R.drawable.cbutton_bg,
    @DrawableRes val captureButtonIcon: Int = R.drawable.camera_shutter,
    @StringRes val captureButtonDescription: Int = R.string.capture,
    @DrawableRes val flipCameraIcon: Int = R.drawable.flip_camera,
    @StringRes val flipCameraDescription: Int = R.string.flip_camera,
    val selfTimerBadge: String = "",
    val selfTimerBadgeVisible: Boolean = false,
)
