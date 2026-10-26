package app.grapheneos.camera.ui.gallery.screen.delegate

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryFullPhoto
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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
            launch {
                trackFullPhoto()
            }

            stateHolder.state
                .distinctUntilChanged(GalleryState::showsSamePageAs)
                .collect { state ->
                    keepPreviewsAround(state).forEach { item ->
                        this@coroutineScope.launch { loadPreview(item) }
                    }
                    keepThumbnailsAround(state).forEach { item ->
                        this@coroutineScope.launch { loadThumbnail(item) }
                    }
                }
        }
    }

    // Videos get no preview: their thumbnail shows until the player draws the first frame.
    private fun keepPreviewsAround(state: GalleryState): List<CapturedItem> {
        val items = state.itemsAround(PREVIEW_RADIUS).filter { it.type == CapturedItemType.IMAGE }
        val missingItems = items.filterNot(state.previews::containsKey)

        stateHolder.update { current ->
            current.copy(
                previews = items.associateWith { item ->
                    current.previews[item] ?: GalleryPreview.Loading
                },
            )
        }

        return missingItems
    }

    private fun keepThumbnailsAround(state: GalleryState): List<CapturedItem> {
        val items = state.itemsAround(THUMBNAIL_RADIUS)
        val missingItems = items.filterNot(state.thumbnails::containsKey)

        stateHolder.update { current ->
            current.copy(
                thumbnails = items.associateWith { item ->
                    current.thumbnails[item] ?: GalleryPreview.Loading
                },
            )
        }

        return missingItems
    }

    private suspend fun loadPreview(item: CapturedItem) {
        val bitmap = capturedItemRepository.loadPhotoPreview(item)

        if (item in stateHolder.state.value.previews) {
            stateHolder.update {
                it.copy(previews = it.previews + (item to previewOf(bitmap)))
            }
        }
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

    private suspend fun trackFullPhoto() {
        coroutineScope {
            launch {
                stateHolder.state
                    .map { state -> state.currentItem() }
                    .distinctUntilChanged()
                    .collect(::dropFullPhotoOfOtherItems)
            }

            stateHolder.state
                .map { state -> state.zoomedPhoto() }
                .distinctUntilChanged()
                .filterNotNull()
                .filter { item -> stateHolder.state.value.fullPhoto?.item != item }
                .collectLatest(::loadFullPhoto)
        }
    }

    private fun dropFullPhotoOfOtherItems(currentItem: CapturedItem?) {
        if (stateHolder.state.value.fullPhoto?.item == currentItem) return

        stateHolder.update { it.copy(fullPhoto = null) }
    }

    private suspend fun loadFullPhoto(item: CapturedItem) {
        val bitmap = capturedItemRepository.loadFullPhoto(item)

        if (item == stateHolder.state.value.currentItem()) {
            stateHolder.update {
                it.copy(
                    fullPhoto = GalleryFullPhoto(
                        item = item,
                        image = previewOf(bitmap),
                    ),
                )
            }
        }
    }

    private fun previewOf(bitmap: Bitmap?): GalleryPreview {
        return when (bitmap) {
            null -> GalleryPreview.Unavailable
            else -> GalleryPreview.Loaded(
                image = bitmap.asImageBitmap(),
                isHdr = hasGainmap(bitmap),
            )
        }
    }

    private fun hasGainmap(bitmap: Bitmap): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && bitmap.hasGainmap()
    }

    private companion object {
        const val PREVIEW_RADIUS = 1
        const val THUMBNAIL_RADIUS = 5
        const val THUMBNAIL_SIDE = 512
    }
}
