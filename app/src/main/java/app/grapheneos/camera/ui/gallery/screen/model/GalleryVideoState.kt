package app.grapheneos.camera.ui.gallery.screen.model

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackUiModel

internal data class GalleryVideoState(
    val item: CapturedItem,
    val playback: VideoPlaybackUiModel,
)
