package app.grapheneos.camera.ui.viewfinder.screen.model

sealed interface SwipeOutcome {

    data object OpenSettingsPanel : SwipeOutcome

    data object CloseSettingsPanel : SwipeOutcome

    data object ShowQrFormats : SwipeOutcome

    data class SelectAdjacentMode(
        val offset: Int,
    ) : SwipeOutcome
}
