package app.grapheneos.camera.ui.videoplayer.screen.model

import androidx.compose.runtime.Immutable
import kotlin.time.Duration

@Immutable
internal data class VideoPlayerUiState(
    val isPlaying: Boolean = false,
    val position: Duration = Duration.ZERO,
    val duration: Duration = Duration.ZERO,
    val aspectRatio: Float? = null,
    val areControlsVisible: Boolean = true,
)
