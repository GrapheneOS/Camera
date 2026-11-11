package app.grapheneos.camera.ui.videoplayer.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackUiModel

@Immutable
internal data class VideoPlayerUiState(
    val video: VideoPlaybackUiModel = VideoPlaybackUiModel.Active(),
    val isMuted: Boolean = false,
    val areControlsVisible: Boolean = true,
)
