package app.grapheneos.camera.ui.viewfinder.screen.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.permission.model.AppPermission

sealed interface ViewfinderScreenEffect {

    data object PlayLevelHaptic : ViewfinderScreenEffect

    data object ShowQrFormats : ViewfinderScreenEffect

    data class ShowMessage(
        @StringRes val message: Int,
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

    data class ShareCapturedItem(
        val item: CapturedItem,
    ) : ViewfinderScreenEffect

    sealed interface Location : ViewfinderScreenEffect {

        data object OpenSettings : Location

        data class ShowDisabled(
            val offersSettings: Boolean,
        ) : Location
    }

    sealed interface CaptureResult : ViewfinderScreenEffect {

        data object PhotoFailed : CaptureResult

        data object PhotoStored : CaptureResult

        data object PhotoStoreFailed : CaptureResult

        data class PhotoReturned(
            val bitmap: Bitmap,
        ) : CaptureResult

        data class VideoReturned(
            val uri: Uri,
        ) : CaptureResult
    }

    sealed interface Recording : ViewfinderScreenEffect {

        data class PlayStartSound(
            val soundEnabled: Boolean,
        ) : Recording

        data class SaveFailed(
            val errorCode: Int,
        ) : Recording

        data class Interrupted(
            val errorCode: Int,
        ) : Recording
    }

    sealed interface Permission : ViewfinderScreenEffect {

        data object OpenSettings : Permission

        data class Request(
            val permission: AppPermission,
            val explainsFirst: Boolean,
        ) : Permission
    }
}
