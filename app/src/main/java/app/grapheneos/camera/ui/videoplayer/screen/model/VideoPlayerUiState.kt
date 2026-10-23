package app.grapheneos.camera.ui.videoplayer.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackUiModel
import kotlin.time.Duration

@Immutable
internal data class VideoPlayerUiState(
    val video: VideoPlaybackUiModel = VideoPlaybackUiModel.Active(),
    val position: Duration = Duration.ZERO,
    val isMuted: Boolean = false,
    val areControlsVisible: Boolean = true,
)
