package app.grapheneos.camera.ui.previewoverlays.screen.model.state

internal sealed interface FocusPress {

    data class OnRing(
        val x: Float,
        val y: Float,
    ) : FocusPress

    data class NewPoint(
        val requestId: Long,
        val x: Float,
        val y: Float,
    ) : FocusPress
}
