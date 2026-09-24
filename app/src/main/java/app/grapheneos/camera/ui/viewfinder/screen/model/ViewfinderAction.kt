package app.grapheneos.camera.ui.viewfinder.screen.model

import android.graphics.Bitmap
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.permission.model.AppPermission
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

        data class PreviewSwiped(
            val direction: SwipeDirection,
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

        data class DisplayRotationChanged(
            val rotation: Int,
        ) : CameraAction
    }

    sealed interface CaptureAction : ViewfinderAction {

        data object ShutterClicked : CaptureAction

        data object CaptureButtonClicked : CaptureAction

        data object CaptureKeyPressed : CaptureAction

        data object PictureCaptureCancelled : CaptureAction

        data object SelfTimerStartClicked : CaptureAction

        data object SelfTimerCancelClicked : CaptureAction

        data object StorageLocationNotFound : CaptureAction

        data object CapturedPreviewShown : CaptureAction

        data class CapturedPreviewConfirmed(
            val bitmap: Bitmap,
        ) : CaptureAction
    }

    sealed interface RecordingAction : ViewfinderAction {

        data object RecordingStopRequested : RecordingAction

        data object StartSoundPlayed : RecordingAction

        data object RecordingRequested : RecordingAction

        data object RecordWithoutAudioClicked : RecordingAction

        data class RecordingPauseToggled(
            val paused: Boolean,
        ) : RecordingAction

        data class RecordingMuteToggled(
            val muted: Boolean,
        ) : RecordingAction
    }

    sealed interface LifecycleAction : ViewfinderAction {

        data object ScreenStarted : LifecycleAction

        data object ScreenStopped : LifecycleAction

        data object ScreenResumed : LifecycleAction

        data object ScreenPaused : LifecycleAction

        data object PreviewStreamingStarted : LifecycleAction

        data object QrResultDismissed : LifecycleAction

        data object CapturedPreviewDismissed : LifecycleAction

        data object ScreenDestroyed : LifecycleAction

        data class ScreenCreated(
            val host: ViewfinderHost,
        ) : LifecycleAction
    }

    sealed interface PermissionAction : ViewfinderAction {

        data object SettingsClicked : PermissionAction

        data class RequestAnswered(
            val permission: AppPermission,
        ) : PermissionAction

        data class RationaleRequired(
            val permission: AppPermission,
        ) : PermissionAction

        data class DialogDismissed(
            val permission: AppPermission,
        ) : PermissionAction
    }

    sealed interface SettingsAction : ViewfinderAction {

        data object ScanAllCodesToggleClicked : SettingsAction

        data object GridToggleClicked : SettingsAction

        data object EnableLocationClicked : SettingsAction

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
