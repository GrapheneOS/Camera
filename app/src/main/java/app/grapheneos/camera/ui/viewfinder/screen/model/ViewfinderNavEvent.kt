package app.grapheneos.camera.ui.viewfinder.screen.model

import android.net.Uri
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.ui.core.model.QuickSetting

sealed interface ViewfinderNavEvent {

    data object CloseScreen : ViewfinderNavEvent

    data class OpenSettingsPanel(
        val highlight: QuickSetting? = null,
    ) : ViewfinderNavEvent

    data object CloseSettingsPanel : ViewfinderNavEvent

    data class OpenCameraSettings(
        val capabilities: CameraCapabilities,
    ) : ViewfinderNavEvent

    data class OpenVideo(
        val uri: Uri,
    ) : ViewfinderNavEvent

    data class OpenGallery(
        val lastCapturedItem: CapturedItem?,
    ) : ViewfinderNavEvent

    data class OpenSecureGallery(
        val capturedItems: List<CapturedItem>,
        val lastCapturedItem: CapturedItem?,
    ) : ViewfinderNavEvent
}
