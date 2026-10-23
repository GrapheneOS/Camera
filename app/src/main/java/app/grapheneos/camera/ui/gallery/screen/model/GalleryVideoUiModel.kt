package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackUiModel

@Immutable
internal data class GalleryVideoUiModel(
    val pageKey: String,
    val playback: VideoPlaybackUiModel,
    val isMuted: Boolean,
) {

    fun isPlaying(): Boolean {
        return (playback as? VideoPlaybackUiModel.Active)?.isPlaying == true
    }
}
