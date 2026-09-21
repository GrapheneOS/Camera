package app.grapheneos.camera.ui.viewfinder.screen.model

import kotlin.time.Duration

data class ViewfinderCaptureState(
    val recordingPhase: RecordingPhase = RecordingPhase.IDLE,
    val recordedDuration: Duration = Duration.ZERO,
    val isRecordingPaused: Boolean = false,
    val isRecordingMuted: Boolean = false,
    val isCapturedPreviewShown: Boolean = false,
    val isTakingPicture: Boolean = false,
    val isSavingPicture: Boolean = false,
    val isSelfTimerRunning: Boolean = false,
)
