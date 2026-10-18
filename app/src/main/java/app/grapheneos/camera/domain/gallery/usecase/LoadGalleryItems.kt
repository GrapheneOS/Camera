package app.grapheneos.camera.domain.gallery.usecase

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.gallery.mapper.GalleryItemsMapper
import javax.inject.Inject

internal interface LoadGalleryItems {
    suspend operator fun invoke(
        videoOnly: Boolean,
        sessionItems: List<CapturedItem>?,
    ): List<CapturedItem>
}

internal class LoadGalleryItemsImpl @Inject constructor(
    private val capturedItemRepository: CapturedItemRepository,
    private val galleryItemsMapper: GalleryItemsMapper,
) : LoadGalleryItems {

    override suspend fun invoke(
        videoOnly: Boolean,
        sessionItems: List<CapturedItem>?,
    ): List<CapturedItem> {
        return galleryItemsMapper.map(
            items = capturedItemRepository.capturedItems(),
            videoOnly = videoOnly,
            sessionItems = sessionItems,
        )
    }
}
