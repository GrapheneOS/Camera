package app.grapheneos.camera.ui.mediaviewer.playback

import androidx.compose.runtime.Immutable
import kotlin.time.Duration

@Immutable
internal sealed interface VideoPlaybackUiModel {

    data class Active(
        val isPlaying: Boolean = false,
        val duration: Duration = Duration.ZERO,
        val aspectRatio: Float? = null,
        val hasRenderedFrame: Boolean = false,
        val isFastForwarding: Boolean = false,
    ) : VideoPlaybackUiModel

    data object Failed : VideoPlaybackUiModel
}
