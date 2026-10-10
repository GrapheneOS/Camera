package app.grapheneos.camera.ui.camerasettings.screen.model

import android.net.Uri

internal sealed interface CameraSettingsAction {

    data object PhotoQualityClicked : CameraSettingsAction

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

    data class SaveImageAsPreviewedToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class SaveVideoAsPreviewedToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class HighestResolutionToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class ZslToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class CameraSoundsToggled(
        override val enabled: Boolean,
    ) : SwitchToggled

    data class PhotoQualityConfirmed(
        val quality: Int,
    ) : CameraSettingsAction

    data class StorageLocationPicked(
        val tree: Uri?,
    ) : CameraSettingsAction
}
