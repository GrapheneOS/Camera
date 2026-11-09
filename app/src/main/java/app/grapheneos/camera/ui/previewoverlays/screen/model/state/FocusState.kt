package app.grapheneos.camera.ui.previewoverlays.screen.model.state

internal data class FocusState(
    val selection: FocusSelection? = null,
    val press: FocusPress? = null,
) {

    fun hidden(): FocusState {
        return FocusState(selection = selection?.copy(visible = false))
    }
}
