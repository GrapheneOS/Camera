package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.grapheneos.camera.R

data class CaptureButtonUiState(
    @DrawableRes val background: Int = R.drawable.cbutton_bg,
    @DrawableRes val icon: Int = R.drawable.camera_shutter,
    @StringRes val description: Int = R.string.capture,
    val enabled: Boolean = true,
    val recording: Boolean = false,
)
