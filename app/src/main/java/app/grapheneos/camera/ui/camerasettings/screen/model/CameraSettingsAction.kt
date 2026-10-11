package app.grapheneos.camera.ui.camerasettings.screen.model

import android.net.Uri
import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.settings.model.GridType

internal sealed interface CameraSettingsAction {

    data object ScreenResumed : CameraSettingsAction

    data object LocationPermissionAnswered : CameraSettingsAction

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

    data object DialogDismissed : CameraSettingsAction

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

    data class GridConfirmed(
        val type: GridType,
    ) : CameraSettingsAction

    data class PhotoQualityConfirmed(
        val quality: Int,
        val isFullResolution: Boolean,
    ) : CameraSettingsAction

    data class CaptureModeConfirmed(
        val mode: CaptureMode,
    ) : CameraSettingsAction

    data class FocusTimeoutConfirmed(
        val seconds: Long,
    ) : CameraSettingsAction

    data object StorageLocationClicked : CameraSettingsAction

    data class StorageLocationPicked(
        val tree: Uri?,
    ) : CameraSettingsAction

    data object StorageLocationResetClicked : CameraSettingsAction
}
