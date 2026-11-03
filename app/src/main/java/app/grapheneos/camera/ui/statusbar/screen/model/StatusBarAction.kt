package app.grapheneos.camera.ui.statusbar.screen.model

internal sealed interface StatusBarAction {

    data object CameraSettingsClicked : StatusBarAction
}
