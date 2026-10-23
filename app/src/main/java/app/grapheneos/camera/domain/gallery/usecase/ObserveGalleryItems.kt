package app.grapheneos.camera.domain.gallery.usecase

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.di.core.DefaultDispatcher
import app.grapheneos.camera.domain.gallery.mapper.GalleryItemsMapper
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

internal interface ObserveGalleryItems {

    /**
     * Emits the items, then again after every storage change and every [refreshes] emission.
     * Each new emission cancels a read that is still running.
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
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
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
        }.flowOn(defaultDispatcher)
    }
}
