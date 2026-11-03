package app.grapheneos.camera.ui.camerasettings.screen.model

internal sealed interface CameraSettingsNavEvent {
    data object OpenLicenses : CameraSettingsNavEvent
}
