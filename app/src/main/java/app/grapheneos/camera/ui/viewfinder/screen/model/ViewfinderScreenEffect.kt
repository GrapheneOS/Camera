package app.grapheneos.camera.ui.viewfinder.screen.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.permission.model.AppPermission

sealed interface ViewfinderScreenEffect {

    data object ShowStorageLocationNotFound : ViewfinderScreenEffect

    data object AnimateLensSwitch : ViewfinderScreenEffect

    data object PlayLevelHaptic : ViewfinderScreenEffect

    data object CloseScreen : ViewfinderScreenEffect

    data class ShowMessage(
        @StringRes val message: Int,
    ) : ViewfinderScreenEffect

    data class ShowVideoQualityUnsupported(
        val quality: VideoQuality,
    ) : ViewfinderScreenEffect

    data class ShowQrResult(
        val text: String,
    ) : ViewfinderScreenEffect

    sealed interface Preview : ViewfinderScreenEffect {

        data class ShowFocus(
            val x: Float,
            val y: Float,
        ) : Preview

        data class Flash(
            val selfIlluminate: Boolean,
        ) : Preview

        data class ApplySelfIllumination(
            val enabled: Boolean,
        ) : Preview
    }

    sealed interface Settings : ViewfinderScreenEffect {

        data object OpenSheet : Settings

        data object CloseSheet : Settings

        data object ShowQrFormats : Settings
    }

    sealed interface ModeTab : ViewfinderScreenEffect {

        data class SelectAdjacent(
            val offset: Int,
        ) : ModeTab

        data class GoTo(
            val mode: CameraMode,
        ) : ModeTab
    }

    sealed interface Gallery : ViewfinderScreenEffect {

        data class Open(
            val lastCapturedItem: CapturedItem?,
            val videoOnly: Boolean,
        ) : Gallery

        data class OpenSecure(
            val capturedItems: List<CapturedItem>,
            val lastCapturedItem: CapturedItem?,
        ) : Gallery

        data class Share(
            val item: CapturedItem,
        ) : Gallery
    }

    sealed interface Location : ViewfinderScreenEffect {

        data object OpenSettings : Location

        data class ShowDisabled(
            val offersSettings: Boolean,
        ) : Location
    }

    sealed interface Panel : ViewfinderScreenEffect {

        data object ShowZoom : Panel

        data object HideZoom : Panel

        data object HideExposure : Panel
    }

    sealed interface SelfTimer : ViewfinderScreenEffect {

        data object Started : SelfTimer

        data object Finished : SelfTimer

        data object Cancelled : SelfTimer

        data class Ticked(
            val secondsLeft: Int,
        ) : SelfTimer
    }

    sealed interface Picture : ViewfinderScreenEffect {

        data object PreviewFailed : Picture

        data object PreviewStored : Picture

        data object PreviewStoreFailed : Picture

        data class PreviewReturned(
            val bitmap: Bitmap,
        ) : Picture

        data class PreviewCaptured(
            val bitmap: Bitmap,
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

    sealed interface Recording : ViewfinderScreenEffect {

        data object PlayStartSound : Recording

        data class ShowForReview(
            val uri: Uri,
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
}
