package app.grapheneos.camera.ui.viewfinder.screen.model.input

sealed interface SwipeOutcome {

    data object ShowQrFormats : SwipeOutcome

    data class SelectAdjacentMode(
        val offset: Int,
    ) : SwipeOutcome
}
