package app.grapheneos.camera.ui.camerasettings.screen.model

import android.net.Uri
import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.VolumeKeyAction

internal sealed interface CameraSettingsAction {

    data object ScreenResumed : CameraSettingsAction

    data object LocationPermissionAnswered : CameraSettingsAction

    data object LocationSettingsClicked : CameraSettingsAction

    sealed interface DialogSettingClicked : CameraSettingsAction {
        val dialog: CameraSettingsDialog
    }

    data object GridClicked : DialogSettingClicked {
        override val dialog: CameraSettingsDialog = CameraSettingsDialog.Grid
    }

    data object PhotoQualityClicked : DialogSettingClicked {
        override val dialog: CameraSettingsDialog = CameraSettingsDialog.PhotoQuality
    }

    data object CaptureModeClicked : DialogSettingClicked {
        override val dialog: CameraSettingsDialog = CameraSettingsDialog.CaptureMode
    }

    data object FocusTimeoutClicked : DialogSettingClicked {
        override val dialog: CameraSettingsDialog = CameraSettingsDialog.FocusTimeout
    }

    data object VolumeKeyActionClicked : DialogSettingClicked {
        override val dialog: CameraSettingsDialog = CameraSettingsDialog.VolumeKeyAction
    }

    data object ResetSettingsClicked : DialogSettingClicked {
        override val dialog: CameraSettingsDialog = CameraSettingsDialog.ResetSettings
    }

    sealed interface SwitchToggled : CameraSettingsAction {
        val enabled: Boolean
    }

    data class GeoTaggingToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class RemoveExifToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class GyroscopeSuggestionsToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class KeepSelfiesAsSeenToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class VideoStabilizationToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class CameraSoundsToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class TimerLightToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data object DialogDismissed : CameraSettingsAction

    sealed interface DialogConfirmed : CameraSettingsAction

    data class GridConfirmed(
        val type: GridType,
    ) : DialogConfirmed

    data class PhotoQualityConfirmed(
        val quality: Int,
        val isFullResolution: Boolean,
    ) : DialogConfirmed

    data class CaptureModeConfirmed(
        val mode: CaptureMode,
    ) : DialogConfirmed

    data class FocusTimeoutConfirmed(
        val seconds: Long,
    ) : DialogConfirmed

    data class VolumeKeyActionConfirmed(
        val action: VolumeKeyAction,
    ) : DialogConfirmed

    data object ResetSettingsConfirmed : DialogConfirmed

    data object StorageLocationClicked : CameraSettingsAction

    data class StorageLocationPicked(
        val tree: Uri?,
    ) : CameraSettingsAction

    data object StorageLocationResetClicked : CameraSettingsAction

    data object LicensesClicked : CameraSettingsAction
}
