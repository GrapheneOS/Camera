package app.grapheneos.camera.ui.camerasettings.screen.model

internal sealed interface CameraSettingsDialog {

    data object Grid : CameraSettingsDialog

    data object PhotoQuality : CameraSettingsDialog

    data object CaptureMode : CameraSettingsDialog

    data object FocusTimeout : CameraSettingsDialog

    data object VolumeKeyAction : CameraSettingsDialog

    data object ResetSettings : CameraSettingsDialog
}
