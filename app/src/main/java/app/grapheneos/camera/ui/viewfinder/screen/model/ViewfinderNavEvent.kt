package app.grapheneos.camera.ui.viewfinder.screen.model

sealed interface ViewfinderNavEvent {

    data object OpenSettingsPanel : ViewfinderNavEvent

    data object CloseSettingsPanel : ViewfinderNavEvent
}
