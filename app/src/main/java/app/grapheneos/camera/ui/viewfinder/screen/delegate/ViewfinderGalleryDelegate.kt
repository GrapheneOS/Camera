package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.graphics.Bitmap
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.coordinator.CapturedItemSession
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderGalleryState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

interface ViewfinderGalleryDelegate {

    fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    )

    fun onScreenCreated(host: ViewfinderHost)

    fun onScreenDestroyed()

    fun refreshThumbnail()

    fun showThumbnail(thumbnail: Bitmap)

    fun recordCapturedItem(item: CapturedItem)

    fun openGallery()

    fun shareLastCapturedItem()
}

internal class ViewfinderGalleryDelegateImpl @Inject constructor(
    private val capturedItemSession: CapturedItemSession,
    private val capturedItemRepository: CapturedItemRepository,
    private val entryPoint: CameraEntryPoint,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderGalleryDelegate {

    private lateinit var scope: CoroutineScope

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    private var host: ViewfinderHost? = null
    private var preparation: Job? = null
    private var thumbnailLoad: Job? = null

    override fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    ) {
        if (isBound) return
        isBound = true

        this.scope = scope
        this.stateHolder = stateHolder

        preparation = scope.launch(mainDispatcher) {
            capturedItemSession.prepare()
        }

        scope.launch(mainDispatcher) {
            capturedItemSession.trackLastCapturedItem()
        }
    }

    override fun onScreenCreated(host: ViewfinderHost) {
        this.host = host
    }

    override fun onScreenDestroyed() {
        thumbnailLoad?.cancel()
        host = null
    }

    override fun refreshThumbnail() {
        val thumbnailSize = host?.thumbnailSize ?: return

        thumbnailLoad?.cancel()
        thumbnailLoad = scope.launch(mainDispatcher) {
            preparation?.join()

            val item = capturedItemSession.lastCapturedItem
            val thumbnail = item?.let {
                capturedItemRepository.loadThumbnail(
                    item = item,
                    targetWidth = thumbnailSize.width,
                    targetHeight = thumbnailSize.height,
                )
            }

            updateGallery { gallery ->
                gallery.copy(thumbnail = thumbnail)
            }
        }
    }

    override fun showThumbnail(thumbnail: Bitmap) {
        thumbnailLoad?.cancel()

        updateGallery { gallery ->
            gallery.copy(thumbnail = thumbnail)
        }
    }

    override fun recordCapturedItem(item: CapturedItem) {
        capturedItemSession.recordCapturedItem(item)

        if (entryPoint.isSecureSession) {
            updateGallery { gallery ->
                gallery.copy(secureCapturedItems = gallery.secureCapturedItems + item)
            }
        }
    }

    override fun openGallery() {
        val gallery = stateHolder.state.value.gallery
        // A loaded thumbnail is what proves the last captured item is still accessible.
        val lastCapturedItem = capturedItemSession.lastCapturedItem
            .takeIf { gallery.thumbnail != null }

        val effect = when {
            !entryPoint.isSecureSession -> {
                Effect.OpenGallery(
                    lastCapturedItem = lastCapturedItem,
                    videoOnly = entryPoint.requiresVideoModeOnly,
                )
            }

            gallery.secureCapturedItems.isEmpty() -> {
                Effect.ShowMessage(R.string.no_image)
            }

            else -> {
                Effect.OpenSecureGallery(
                    capturedItems = gallery.secureCapturedItems,
                    lastCapturedItem = lastCapturedItem,
                )
            }
        }

        stateHolder.postEffect(effect)
    }

    override fun shareLastCapturedItem() {
        val item = capturedItemSession.lastCapturedItem

        val effect = when {
            entryPoint.isSecureSession -> {
                Effect.ShowMessage(R.string.sharing_not_allowed)
            }

            item == null -> {
                Effect.ShowMessage(R.string.please_wait_for_image_to_get_captured_before_sharing)
            }

            else -> {
                Effect.ShareCapturedItem(item)
            }
        }

        stateHolder.postEffect(effect)
    }

    private fun updateGallery(transform: (ViewfinderGalleryState) -> ViewfinderGalleryState) {
        stateHolder.update { state ->
            state.copy(gallery = transform(state.gallery))
        }
    }
}
