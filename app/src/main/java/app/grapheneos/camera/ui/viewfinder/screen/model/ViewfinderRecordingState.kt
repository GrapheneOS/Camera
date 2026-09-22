package app.grapheneos.camera.ui.viewfinder.screen.model

import kotlin.time.Duration

data class ViewfinderRecordingState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val duration: Duration = Duration.ZERO,
    val isPaused: Boolean = false,
    val isMuted: Boolean = false,
) {

    fun isActive(): Boolean {
        return phase != RecordingPhase.IDLE
    }

    fun isRecording(): Boolean {
        return phase == RecordingPhase.RECORDING
    }
}
