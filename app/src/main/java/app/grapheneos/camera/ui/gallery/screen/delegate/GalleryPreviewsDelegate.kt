package app.grapheneos.camera.ui.gallery.screen.delegate

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal interface GalleryPreviewsDelegate {

    fun bind(stateHolder: GalleryStateHolder)

    suspend fun trackPreviews()
}

internal class GalleryPreviewsDelegateImpl @Inject constructor(
    private val capturedItemRepository: CapturedItemRepository,
) : GalleryPreviewsDelegate {

    private lateinit var stateHolder: GalleryStateHolder

    override fun bind(stateHolder: GalleryStateHolder) {
        this.stateHolder = stateHolder
    }

    override suspend fun trackPreviews() {
        coroutineScope {
            stateHolder.state
                .distinctUntilChanged(GalleryState::showsSamePageAs)
                .collect { state ->
                    keepThumbnailsAround(state).forEach { item ->
                        this@coroutineScope.launch { loadThumbnail(item) }
                    }
                    keepPreviewsAround(state).forEach { item ->
                        this@coroutineScope.launch { loadPreview(item) }
                    }
                }
        }
    }

    private fun keepThumbnailsAround(state: GalleryState): List<CapturedItem> {
        val items = state.itemsAround(THUMBNAIL_RADIUS)
        val missingItems = items.filterNot(state.thumbnails::containsKey)

        stateHolder.update {
            it.copy(
                thumbnails = it.thumbnails.filterKeys(items::contains) +
                    missingItems.associateWith { GalleryPreview.Loading },
            )
        }

        return missingItems
    }

    private fun keepPreviewsAround(state: GalleryState): List<CapturedItem> {
        val items = state.itemsAround(PREVIEW_RADIUS)
        val missingItems = items.filterNot(state.previews::containsKey)

        stateHolder.update {
            it.copy(
                previews = it.previews.filterKeys(items::contains) +
                    missingItems.associateWith { GalleryPreview.Loading },
            )
        }

        return missingItems
    }

    private suspend fun loadThumbnail(item: CapturedItem) {
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

    private suspend fun loadPreview(item: CapturedItem) {
        val bitmap = capturedItemRepository.loadPreview(item)

        if (item in stateHolder.state.value.previews) {
            stateHolder.update {
                it.copy(previews = it.previews + (item to previewOf(bitmap)))
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
