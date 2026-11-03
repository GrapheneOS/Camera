package app.grapheneos.camera.ui.viewfinder.screen

internal class SettingsPanelDragCallbacks(
    val isShown: () -> Boolean,
    val onDragStarted: () -> Unit,
    val onDragged: (delta: Float) -> Unit,
    val onDragStopped: (velocity: Float) -> Unit,
)
