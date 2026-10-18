package app.grapheneos.camera.domain.gallery.mapper

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import javax.inject.Inject

internal interface GalleryItemsMapper {
    fun map(
        items: List<CapturedItem>,
        videoOnly: Boolean,
        sessionItems: List<CapturedItem>?,
    ): List<CapturedItem>
}

internal class GalleryItemsMapperImpl @Inject constructor() : GalleryItemsMapper {

    override fun map(
        items: List<CapturedItem>,
        videoOnly: Boolean,
        sessionItems: List<CapturedItem>?,
    ): List<CapturedItem> {
        val session = sessionItems?.toHashSet()

        return items
            .filter { item -> !videoOnly || item.type == CapturedItemType.VIDEO }
            .filter { item -> session == null || item in session }
            .sortedByDescending { item -> item.dateString }
    }
}
