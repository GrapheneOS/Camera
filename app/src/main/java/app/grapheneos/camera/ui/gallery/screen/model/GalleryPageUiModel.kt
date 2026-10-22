package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class GalleryPageUiModel(
    val key: String,
    val name: String,
    val isVideo: Boolean,
)
