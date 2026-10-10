package app.grapheneos.camera.ui.camerasettings.screen.model

internal sealed interface CameraSettingsDialog {

    data object Grid : CameraSettingsDialog

    data object PhotoQuality : CameraSettingsDialog

    data object CaptureMode : CameraSettingsDialog

    data object FocusTimeout : CameraSettingsDialog
}
