package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.annotation.StringRes
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality

sealed interface ViewfinderScreenEffect {

    data object ShowZoomPanel : ViewfinderScreenEffect

    data object HideZoomPanel : ViewfinderScreenEffect

    data object HideExposurePanel : ViewfinderScreenEffect

    data object StartLocationUpdates : ViewfinderScreenEffect

    data object StopLocationUpdates : ViewfinderScreenEffect

    data object ShowStorageLocationNotFound : ViewfinderScreenEffect

    data class ShowMessage(
        @StringRes val message: Int,
    ) : ViewfinderScreenEffect

    data class ApplySelfIllumination(
        val enabled: Boolean,
    ) : ViewfinderScreenEffect

    data class ShowVideoQualityUnsupported(
        val quality: VideoQuality,
    ) : ViewfinderScreenEffect

    data class ShowQrResult(
        val text: String,
    ) : ViewfinderScreenEffect

    data class FlashPreview(
        val selfIlluminate: Boolean,
    ) : ViewfinderScreenEffect

    data class GoToModeTab(
        val mode: CameraMode,
    ) : ViewfinderScreenEffect

    sealed interface SelfTimer : ViewfinderScreenEffect {

        data object Started : SelfTimer

        data object Finished : SelfTimer

        data object Cancelled : SelfTimer

        data class Ticked(
            val secondsLeft: Int,
        ) : SelfTimer
    }
}
