package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.runtime.Immutable

@Immutable
internal sealed interface GalleryMenuUiModel {

    data object Photo : GalleryMenuUiModel

    data class Video(
        val isLooping: Boolean,
        val playbackSpeed: Float,
    ) : GalleryMenuUiModel
}
