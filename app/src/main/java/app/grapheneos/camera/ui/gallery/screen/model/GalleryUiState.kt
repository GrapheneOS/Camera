package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Immutable
internal data class GalleryUiState(
    val pages: ImmutableList<GalleryPageUiModel> = persistentListOf(),
    val previews: ImmutableMap<String, GalleryPreview> = persistentMapOf(),
    val details: ImmutableMap<String, GalleryDetails> = persistentMapOf(),
    val currentPage: Int = 0,
    val isEmpty: Boolean = false,
    val capturedAt: LocalDateTime? = null,
    val showsExternalActions: Boolean = true,
    val showsSystemGallery: Boolean = false,
    val showsUseAs: Boolean = false,
    val showsHdr: Boolean = false,
    val areControlsVisible: Boolean = true,
    val isZoomed: Boolean = false,
    val showsDetails: Boolean = false,
    val dialog: GalleryDialog? = null,
    val trashedPageKey: String? = null,
)
