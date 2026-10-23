package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class GalleryUiState(
    val pager: GalleryPagerUiModel = GalleryPagerUiModel(),
    val bars: GalleryBarsUiModel = GalleryBarsUiModel(),
    val video: GalleryVideoUiModel? = null,
    val chrome: GalleryChromeUiModel = GalleryChromeUiModel(),
    val isZoomed: Boolean = false,
    val showsDetails: Boolean = false,
    val showsHdr: Boolean = false,
    val dialog: GalleryDialog? = null,
    val trashedPageKey: String? = null,
)
