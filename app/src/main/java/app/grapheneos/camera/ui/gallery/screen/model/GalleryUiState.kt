package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class GalleryUiState(
    val pages: ImmutableList<GalleryPageUiModel> = persistentListOf(),
    val currentPage: Int = 0,
    val capturedAt: LocalDateTime? = null,
    val showsExternalActions: Boolean = true,
    val showsSystemGallery: Boolean = false,
    val areControlsVisible: Boolean = true,
    val isZoomed: Boolean = false,
    val showsDetails: Boolean = false,
    val dialog: GalleryDialog? = null,
)
