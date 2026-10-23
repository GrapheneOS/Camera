package app.grapheneos.camera.ui.camerasettings.screen.model

internal sealed interface CameraSettingsScreenEffect {

    sealed interface OutsideSession : CameraSettingsScreenEffect

    data object RequestLocationPermission : CameraSettingsScreenEffect

    data object OpenAppSettings : OutsideSession

    data object PickStorageLocation : OutsideSession

    data object OpenLicenses : OutsideSession
}
