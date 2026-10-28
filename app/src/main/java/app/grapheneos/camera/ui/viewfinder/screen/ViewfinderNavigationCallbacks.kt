package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderNavEvent as NavEvent

internal class ViewfinderNavigationCallbacks(
    val onOpenSettingsPanel: () -> Unit,
    val onCloseSettingsPanel: () -> Unit,
) {

    fun navigate(event: NavEvent) {
        when (event) {
            NavEvent.OpenSettingsPanel -> onOpenSettingsPanel()
            NavEvent.CloseSettingsPanel -> onCloseSettingsPanel()
        }
    }
}
