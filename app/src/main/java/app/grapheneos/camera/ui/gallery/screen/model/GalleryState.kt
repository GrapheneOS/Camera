package app.grapheneos.camera.ui.gallery.screen.model

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType

internal data class GalleryState(
    val items: List<CapturedItem> = emptyList(),
    val thumbnails: Map<CapturedItem, GalleryPreview> = emptyMap(),
    val previews: Map<CapturedItem, GalleryPreview> = emptyMap(),
    val fullPhoto: GalleryFullPhoto? = null,
    val details: Map<CapturedItem, GalleryDetails> = emptyMap(),
    val currentIndex: Int = 0,
    val settledIndex: Int? = null,
    val isLoaded: Boolean = false,
    val isSecureSession: Boolean = false,
    val hasSystemGallery: Boolean = false,
    val areControlsVisible: Boolean = true,
    val isZoomed: Boolean = false,
    val showsDetails: Boolean = false,
    val dialog: GalleryDialog? = null,
    val trashedItem: GalleryTrashedItem? = null,
    val video: GalleryVideoState? = null,
    val playbackOptions: GalleryPlaybackOptions = GalleryPlaybackOptions(),
) {

    fun currentItem(): CapturedItem? {
        return items.getOrNull(currentIndex)
    }

    fun zoomedPhoto(): CapturedItem? {
        return currentItem()?.takeIf { isZoomed && it.type == CapturedItemType.IMAGE }
    }

    fun settledVideo(): CapturedItem? {
        return settledIndex
            ?.let(items::getOrNull)
            ?.takeIf { it.type == CapturedItemType.VIDEO }
    }

    fun canHideControls(): Boolean {
        return video?.isPlaying() == true && areControlsVisible && !showsDetails && dialog == null
    }

    fun itemsAround(radius: Int): List<CapturedItem> {
        val center = settledOrCurrentIndex()
        val nearestFirst = (0..radius).flatMap { distance ->
            listOf(center + distance, center - distance).distinct()
        }

        return nearestFirst.mapNotNull(items::getOrNull)
    }

    fun showsSamePageAs(other: GalleryState): Boolean {
        return settledOrCurrentIndex() == other.settledOrCurrentIndex() && items === other.items
    }

    fun withItems(items: List<CapturedItem>): GalleryState {
        return copy(
            items = items,
            currentIndex = indexIn(
                items = items,
                index = currentIndex,
            ),
            settledIndex = settledIndex?.let { index ->
                indexIn(
                    items = items,
                    index = index,
                )
            },
        )
    }

    private fun settledOrCurrentIndex(): Int {
        return settledIndex ?: currentIndex
    }

    private fun indexIn(
        items: List<CapturedItem>,
        index: Int,
    ): Int {
        val newIndex = items.indexOf(this.items.getOrNull(index))

        return when {
            newIndex >= 0 -> newIndex
            else -> index.coerceAtMost(items.lastIndex).coerceAtLeast(0)
        }
    }
}
