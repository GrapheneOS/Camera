package app.grapheneos.camera.ui.gallery.screen.delegate

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal interface GalleryPreviewsDelegate {

    fun bind(
        scope: CoroutineScope,
        stateHolder: GalleryStateHolder,
    )

    fun loadAround(page: Int)
}

internal class GalleryPreviewsDelegateImpl @Inject constructor(
    private val capturedItemRepository: CapturedItemRepository,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : GalleryPreviewsDelegate {

    private lateinit var scope: CoroutineScope
    private lateinit var stateHolder: GalleryStateHolder

    override fun bind(
        scope: CoroutineScope,
        stateHolder: GalleryStateHolder,
    ) {
        this.scope = scope
        this.stateHolder = stateHolder
    }

    override fun loadAround(page: Int) {
        val state = stateHolder.state.value
        val thumbnailItems = state.itemsAround(
            page = page,
            radius = THUMBNAIL_RADIUS,
        )
        val previewItems = state.itemsAround(
            page = page,
            radius = PREVIEW_RADIUS,
        )

        stateHolder.update {
            it.copy(
                thumbnails = it.thumbnails.filterKeys(thumbnailItems::contains),
                previews = it.previews.filterKeys(previewItems::contains),
            )
        }
        thumbnailItems
            .filterNot(stateHolder.state.value.thumbnails::containsKey)
            .forEach(::loadThumbnail)
        previewItems
            .filterNot(stateHolder.state.value.previews::containsKey)
            .forEach(::loadPreview)
    }

    private fun loadThumbnail(item: CapturedItem) {
        stateHolder.update {
            it.copy(thumbnails = it.thumbnails + (item to GalleryPreview.Loading))
        }

        scope.launch(mainDispatcher) {
            val bitmap = capturedItemRepository.loadThumbnail(
                item = item,
                targetWidth = THUMBNAIL_SIDE,
                targetHeight = THUMBNAIL_SIDE,
            )

            if (item in stateHolder.state.value.thumbnails) {
                stateHolder.update {
                    it.copy(thumbnails = it.thumbnails + (item to previewOf(bitmap)))
                }
            }
        }
    }

    private fun loadPreview(item: CapturedItem) {
        stateHolder.update {
            it.copy(previews = it.previews + (item to GalleryPreview.Loading))
        }

        scope.launch(mainDispatcher) {
            val bitmap = capturedItemRepository.loadPreview(item)

            if (item in stateHolder.state.value.previews) {
                stateHolder.update {
                    it.copy(previews = it.previews + (item to previewOf(bitmap)))
                }
            }
        }
    }

    private fun previewOf(bitmap: Bitmap?): GalleryPreview {
        return when (bitmap) {
            null -> GalleryPreview.Unavailable
            else -> GalleryPreview.Loaded(bitmap.asImageBitmap())
        }
    }

    private companion object {
        const val PREVIEW_RADIUS = 1
        const val THUMBNAIL_RADIUS = 5
        const val THUMBNAIL_SIDE = 512
    }
}
