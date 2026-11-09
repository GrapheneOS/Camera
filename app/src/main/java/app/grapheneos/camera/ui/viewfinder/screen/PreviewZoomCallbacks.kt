package app.grapheneos.camera.ui.viewfinder.screen

internal class PreviewZoomCallbacks(
    val onPinchStarted: () -> Unit,
    val onPinchScaleChanged: (scaleFactor: Float) -> Unit,
    val onPinchFinished: () -> Unit,
)
