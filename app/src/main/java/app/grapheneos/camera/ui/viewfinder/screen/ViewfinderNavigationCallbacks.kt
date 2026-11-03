package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.ui.core.model.QuickSetting
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderNavEvent as NavEvent

internal class ViewfinderNavigationCallbacks(
    val onOpenSettingsPanel: (highlight: QuickSetting?) -> Unit,
    val onCloseSettingsPanel: () -> Unit,
) {

    fun navigate(event: NavEvent) {
        when (event) {
            is NavEvent.OpenSettingsPanel -> onOpenSettingsPanel(event.highlight)
            is NavEvent.CloseSettingsPanel -> onCloseSettingsPanel()
        }
    }
}
