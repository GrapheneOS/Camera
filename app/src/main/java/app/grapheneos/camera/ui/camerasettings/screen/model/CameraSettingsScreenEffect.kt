package app.grapheneos.camera.ui.camerasettings.screen.model

internal sealed interface CameraSettingsScreenEffect {

    data object RequestLocationPermission : CameraSettingsScreenEffect

    data object PickStorageLocation : CameraSettingsScreenEffect
}
