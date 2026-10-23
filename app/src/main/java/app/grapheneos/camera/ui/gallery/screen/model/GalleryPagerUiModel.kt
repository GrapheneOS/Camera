package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Immutable
internal data class GalleryPagerUiModel(
    val pages: ImmutableList<GalleryPageUiModel> = persistentListOf(),
    val previews: ImmutableMap<String, GalleryPreview> = persistentMapOf(),
    val details: ImmutableMap<String, GalleryDetails> = persistentMapOf(),
    val currentPage: Int = 0,
    val isEmpty: Boolean = false,
    val canOpenMap: Boolean = true,
)
