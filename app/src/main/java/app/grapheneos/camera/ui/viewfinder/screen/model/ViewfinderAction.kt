package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.camera.video.Quality
import app.grapheneos.camera.data.core.model.CameraMode

sealed interface ViewfinderAction {

    sealed interface CameraAction : ViewfinderAction {

        data object LensSwitchClicked : CameraAction

        data object FlashToggleClicked : CameraAction

        data object AspectRatioToggleClicked : CameraAction

        data class ModeSelected(
            val mode: CameraMode,
        ) : CameraAction
    }

    sealed interface CaptureAction : ViewfinderAction {

        data object PictureCaptured : CaptureAction

        data object StorageLocationNotFound : CaptureAction
    }

    sealed interface LifecycleAction : ViewfinderAction {

        data object CameraPermissionGranted : LifecycleAction

        data object ScreenResumed : LifecycleAction

        data object PreviewStreamingStarted : LifecycleAction

        data object RecordAudioPermissionGranted : LifecycleAction

        data object QrResultDismissed : LifecycleAction

        data object CapturedPreviewDismissed : LifecycleAction
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
            val quality: Quality,
        ) : SettingsAction
    }
}
