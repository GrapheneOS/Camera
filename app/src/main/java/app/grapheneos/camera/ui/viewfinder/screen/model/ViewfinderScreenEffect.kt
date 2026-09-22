package app.grapheneos.camera.ui.viewfinder.screen.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality

sealed interface ViewfinderScreenEffect {

    data object ShowStorageLocationNotFound : ViewfinderScreenEffect

    data class SetLocationUpdates(
        val enabled: Boolean,
    ) : ViewfinderScreenEffect

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

    sealed interface Picture : ViewfinderScreenEffect {

        data object Captured : Picture

        data object PreviewFailed : Picture

        data class PreviewCaptured(
            val bitmap: Bitmap,
        ) : Picture

        data class Saved(
            val item: CapturedItem,
        ) : Picture

        data class ThumbnailReady(
            val thumbnail: Bitmap,
        ) : Picture

        data class CaptureFailed(
            val errorCode: Int,
            val details: PictureFailureDetails,
        ) : Picture

        data class SaveFailed(
            val stage: String,
            val details: PictureFailureDetails,
            val alreadyReported: Boolean,
        ) : Picture
    }

    sealed interface Panel : ViewfinderScreenEffect {

        data object ShowZoom : Panel

        data object HideZoom : Panel

        data object HideExposure : Panel
    }

    sealed interface Recording : ViewfinderScreenEffect {

        data object PlayStartSound : Recording

        data object PlayStopSound : Recording

        data object RequestAudioPermission : Recording

        data object Stopped : Recording

        data class Saved(
            val uri: Uri,
            val item: CapturedItem?,
        ) : Recording

        data class SaveFailed(
            val errorCode: Int,
        ) : Recording

        data class Interrupted(
            val errorCode: Int,
        ) : Recording
    }

    sealed interface SelfTimer : ViewfinderScreenEffect {

        data object Started : SelfTimer

        data object Finished : SelfTimer

        data object Cancelled : SelfTimer

        data class Ticked(
            val secondsLeft: Int,
        ) : SelfTimer
    }
}
