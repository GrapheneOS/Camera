package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.LensFacing

@Immutable
data class FlipCameraUiModel(
    val visible: Boolean = true,
    val lensFacing: LensFacing = LensFacing.BACK,
    @DrawableRes val icon: Int = R.drawable.flip_camera,
    @StringRes val description: Int = R.string.flip_camera,
)
