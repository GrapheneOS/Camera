package app.grapheneos.camera.domain.gallery.usecase

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.gallery.mapper.GalleryItemsMapper
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

internal interface ObserveGalleryItems {

    /**
     * Lists the items again on every storage change and on every [refreshes] emission. A newer
     * request drops a read still in progress, so a caller that changed the items and then
     * refreshes never gets a list read before its change.
     */
    operator fun invoke(
        videoOnly: Boolean,
        sessionItems: List<CapturedItem>?,
        refreshes: Flow<Unit>,
    ): Flow<List<CapturedItem>>
}

internal class ObserveGalleryItemsImpl @Inject constructor(
    private val capturedItemRepository: CapturedItemRepository,
    private val galleryItemsMapper: GalleryItemsMapper,
) : ObserveGalleryItems {

    override fun invoke(
        videoOnly: Boolean,
        sessionItems: List<CapturedItem>?,
        refreshes: Flow<Unit>,
    ): Flow<List<CapturedItem>> {
        return channelFlow {
            merge(capturedItemRepository.capturedItemsChanges(), refreshes)
                .onStart { emit(Unit) }
                .collectLatest {
                    val items = galleryItemsMapper.map(
                        items = capturedItemRepository.capturedItems(),
                        videoOnly = videoOnly,
                        sessionItems = sessionItems,
                    )

                    send(items)
                }
        }
    }
}
