package app.grapheneos.camera.ui.viewfinder.screen.model

data class RecordingUiState(
    val isActive: Boolean = false,
    val isMuted: Boolean = false,
    val muteToggleVisible: Boolean = false,
    val micOffIconVisible: Boolean = false,
    val timerVisible: Boolean = false,
    val timerText: String = "",
)
