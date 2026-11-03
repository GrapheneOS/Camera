package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.ui.core.model.QuickSetting

sealed interface ViewfinderNavEvent {

    data class OpenSettingsPanel(
        val highlight: QuickSetting? = null,
    ) : ViewfinderNavEvent

    data object CloseSettingsPanel : ViewfinderNavEvent
}
