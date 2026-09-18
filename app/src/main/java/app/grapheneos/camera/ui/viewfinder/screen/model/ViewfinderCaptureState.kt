package app.grapheneos.camera.ui.viewfinder.screen.model

data class ViewfinderCaptureState(
    val recordingPhase: RecordingPhase = RecordingPhase.IDLE,
    val isRecordingPaused: Boolean = false,
    val isCapturedPreviewShown: Boolean = false,
    val isTakingPicture: Boolean = false,
    val isSavingPicture: Boolean = false,
    val isSelfTimerRunning: Boolean = false,
)
