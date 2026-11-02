package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.grapheneos.camera.R

data class FlipCameraUiState(
    val visible: Boolean = true,
    @DrawableRes val icon: Int = R.drawable.flip_camera,
    @StringRes val description: Int = R.string.flip_camera,
)
