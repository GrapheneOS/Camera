package app.grapheneos.camera.ui.viewfinder.screen.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.permission.model.AppPermission

sealed interface ViewfinderScreenEffect {

    data object ShowStorageLocationNotFound : ViewfinderScreenEffect

    data object CloseScreen : ViewfinderScreenEffect

    data object PlayLevelHaptic : ViewfinderScreenEffect

    data object OpenLocationSettings : ViewfinderScreenEffect

    data class ShowLocationDisabled(
        val offersSettings: Boolean,
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

        data object PreviewReturned : Picture

        data object PreviewStored : Picture

        data object PreviewStoreFailed : Picture

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

    sealed interface Permission : ViewfinderScreenEffect {

        data object DismissDialog : Permission

        data object OpenSettings : Permission

        data class Request(
            val permission: AppPermission,
            val explainsFirst: Boolean,
        ) : Permission

        data class ShowDialog(
            val permission: AppPermission,
            val offersSettings: Boolean,
        ) : Permission
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
