package app.grapheneos.camera.ui.previewoverlays.screen.model

internal sealed interface PreviewOverlaysAction {

    data class PreviewContentChanged(
        val content: PreviewContentState,
    ) : PreviewOverlaysAction

    data class PreviewFlashVisibilityChanged(
        val visible: Boolean,
    ) : PreviewOverlaysAction
}
