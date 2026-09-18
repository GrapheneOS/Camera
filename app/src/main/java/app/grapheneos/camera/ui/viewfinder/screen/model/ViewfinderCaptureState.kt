package app.grapheneos.camera.ui.viewfinder.screen.model

data class ViewfinderCaptureState(
    val isRecording: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val isCapturedPreviewShown: Boolean = false,
    val isTakingPicture: Boolean = false,
    val isSavingPicture: Boolean = false,
    val isSelfTimerRunning: Boolean = false,
)
