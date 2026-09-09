package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.annotation.StringRes
import androidx.camera.video.Quality
import app.grapheneos.camera.data.core.model.CameraMode

sealed interface ViewfinderScreenEffect {

    data object ShowZoomPanel : ViewfinderScreenEffect

    data object HideZoomPanel : ViewfinderScreenEffect

    data object ApplySelfIllumination : ViewfinderScreenEffect

    data object ResetTorchToggle : ViewfinderScreenEffect

    data object ReloadVideoQualities : ViewfinderScreenEffect

    data object StartLocationUpdates : ViewfinderScreenEffect

    data object StopLocationUpdates : ViewfinderScreenEffect

    data object ShowStorageLocationNotFound : ViewfinderScreenEffect

    data class ShowMessage(
        @StringRes val message: Int,
    ) : ViewfinderScreenEffect

    data class ShowVideoQualityUnsupported(
        val quality: Quality,
    ) : ViewfinderScreenEffect

    data class FlashPreview(
        val selfIlluminate: Boolean,
    ) : ViewfinderScreenEffect

    data class GoToModeTab(
        val mode: CameraMode,
    ) : ViewfinderScreenEffect
}
