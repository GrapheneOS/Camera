package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost

sealed interface ViewfinderAction {

    sealed interface CameraAction : ViewfinderAction {

        data object LensSwitchClicked : CameraAction

        data object FlashToggleClicked : CameraAction

        data object TorchToggleClicked : CameraAction

        data object AspectRatioToggleClicked : CameraAction

        data object ZoomInKeyPressed : CameraAction

        data object ZoomOutKeyPressed : CameraAction

        data object FocusKeyPressed : CameraAction

        data class PreviewTapped(
            val x: Float,
            val y: Float,
        ) : CameraAction

        data class PreviewPinched(
            val scaleFactor: Float,
        ) : CameraAction

        data class ZoomSliderDragged(
            val linearZoom: Float,
        ) : CameraAction

        data class ExposureSliderDragged(
            val compensationIndex: Int,
        ) : CameraAction

        data class ModeSelected(
            val mode: CameraMode,
        ) : CameraAction
    }

    sealed interface CaptureAction : ViewfinderAction {

        data object PictureCaptured : CaptureAction

        data object StorageLocationNotFound : CaptureAction

        data object RecordingStarted : CaptureAction

        data object RecordingStopped : CaptureAction

        data object CapturedPreviewShown : CaptureAction

        data class RecordingPauseToggled(
            val paused: Boolean,
        ) : CaptureAction
    }

    sealed interface LifecycleAction : ViewfinderAction {

        data object CameraPermissionGranted : LifecycleAction

        data object ScreenResumed : LifecycleAction

        data object PreviewStreamingStarted : LifecycleAction

        data object RecordAudioPermissionGranted : LifecycleAction

        data object QrResultDismissed : LifecycleAction

        data object CapturedPreviewDismissed : LifecycleAction

        data object ScreenDestroyed : LifecycleAction

        data class ScreenCreated(
            val host: ViewfinderHost,
        ) : LifecycleAction
    }

    sealed interface SettingsAction : ViewfinderAction {

        data object ScanAllCodesToggleClicked : SettingsAction

        data object GridToggleClicked : SettingsAction

        data class AudioToggled(
            val enabled: Boolean,
        ) : SettingsAction

        data class GeoTaggingToggled(
            val enabled: Boolean,
        ) : SettingsAction

        data class SelfIlluminationToggled(
            val enabled: Boolean,
        ) : SettingsAction

        data class StabilizationToggled(
            val enabled: Boolean,
        ) : SettingsAction

        data class FocusLockToggled(
            val enabled: Boolean,
        ) : SettingsAction

        data class FocusTimeoutSelected(
            val seconds: Long,
        ) : SettingsAction

        data class SelfTimerSelected(
            val seconds: Int,
        ) : SettingsAction

        data class VideoQualitySelected(
            val quality: VideoQuality,
        ) : SettingsAction
    }
}
