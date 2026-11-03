package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.grapheneos.camera.R

data class FlipCameraUiModel(
    val visible: Boolean = true,
    val flipped: Boolean = false,
    @DrawableRes val icon: Int = R.drawable.flip_camera,
    @StringRes val description: Int = R.string.flip_camera,
)
