package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime

@Immutable
internal data class GalleryBarsUiModel(
    val capturedAt: LocalDateTime? = null,
    val showsItemActions: Boolean = false,
    val showsExternalActions: Boolean = true,
    val showsSystemGallery: Boolean = false,
    val menu: GalleryMenuUiModel? = null,
)
