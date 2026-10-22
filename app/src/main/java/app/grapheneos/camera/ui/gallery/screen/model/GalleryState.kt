package app.grapheneos.camera.ui.gallery.screen.model

import app.grapheneos.camera.data.media.model.CapturedItem

internal data class GalleryState(
    val items: List<CapturedItem> = emptyList(),
    val thumbnails: Map<CapturedItem, GalleryPreview> = emptyMap(),
    val previews: Map<CapturedItem, GalleryPreview> = emptyMap(),
    val details: Map<CapturedItem, GalleryDetails> = emptyMap(),
    val currentIndex: Int = 0,
    val isLoaded: Boolean = false,
    val isSecureSession: Boolean = false,
    val hasSystemGallery: Boolean = false,
    val areControlsVisible: Boolean = true,
    val isZoomed: Boolean = false,
    val showsDetails: Boolean = false,
    val dialog: GalleryDialog? = null,
    val trashedItem: GalleryTrashedItem? = null,
) {

    fun currentItem(): CapturedItem? {
        return items.getOrNull(currentIndex)
    }

    fun currentIndexIn(items: List<CapturedItem>): Int {
        val index = items.indexOf(currentItem())

        return when {
            index >= 0 -> index
            else -> currentIndex.coerceAtMost(items.lastIndex).coerceAtLeast(0)
        }
    }

    fun itemsAround(radius: Int): List<CapturedItem> {
        return (currentIndex - radius..currentIndex + radius).mapNotNull(items::getOrNull)
    }

    fun showsSamePageAs(other: GalleryState): Boolean {
        return currentIndex == other.currentIndex && items == other.items
    }
}
