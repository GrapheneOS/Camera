package app.grapheneos.camera.ui.camerasettings.screen.model

import android.net.Uri
import app.grapheneos.camera.data.core.model.CaptureMode

internal sealed interface CameraSettingsAction {

    data object PhotoQualityClicked : CameraSettingsAction

    data object CaptureModeClicked : CameraSettingsAction

    data object StorageLocationClicked : CameraSettingsAction

    data object StorageLocationResetClicked : CameraSettingsAction

    data object DialogDismissed : CameraSettingsAction

    sealed interface SwitchToggled : CameraSettingsAction {
        val enabled: Boolean
    }

    data class RemoveExifToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class GyroscopeSuggestionsToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class KeepSelfiesAsSeenToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class CameraSoundsToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class PhotoQualityConfirmed(
        val quality: Int,
        val isFullResolution: Boolean,
    ) : CameraSettingsAction

    data class CaptureModeConfirmed(
        val mode: CaptureMode,
    ) : CameraSettingsAction

    data class StorageLocationPicked(
        val tree: Uri?,
    ) : CameraSettingsAction
}
