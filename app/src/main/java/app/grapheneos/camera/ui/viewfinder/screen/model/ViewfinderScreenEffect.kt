package app.grapheneos.camera.ui.viewfinder.screen.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.permission.model.AppPermission

sealed interface ViewfinderScreenEffect {

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

        data class Flash(
            val selfIlluminate: Boolean,
        ) : Preview
    }

    sealed interface Settings : ViewfinderScreenEffect {

        data object ShowQrFormats : Settings

        data class OpenCameraSettings(
            val capabilities: CameraCapabilities,
        ) : Settings
    }

    sealed interface Gallery : ViewfinderScreenEffect {

        data class Open(
            val lastCapturedItem: CapturedItem?,
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

        data class OpenVideo(
            val uri: Uri,
        ) : CaptureResult
    }

    sealed interface Recording : ViewfinderScreenEffect {

        data object PlayStartSound : Recording

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
