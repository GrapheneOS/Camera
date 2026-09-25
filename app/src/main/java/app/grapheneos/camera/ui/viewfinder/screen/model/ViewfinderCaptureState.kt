package app.grapheneos.camera.ui.viewfinder.screen.model

data class ViewfinderCaptureState(
    val isCapturedPreviewShown: Boolean = false,
    val isTakingPicture: Boolean = false,
    val isSavingPicture: Boolean = false,
    val isSelfTimerRunning: Boolean = false,
)
