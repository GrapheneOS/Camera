package app.grapheneos.camera.ui.viewfinder.screen

internal class PreviewFocusCallbacks(
    val onPressed: (x: Float, y: Float) -> Unit,
    val onTapped: () -> Unit,
    val onLongPressed: () -> Unit,
    val onGestureStarted: () -> Unit,
)
