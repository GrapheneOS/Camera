package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.graphics.Bitmap
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.capture.model.CaptureImageRequest
import app.grapheneos.camera.domain.capture.model.CapturePreviewResult
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.usecase.CaptureImage
import app.grapheneos.camera.domain.capture.usecase.CapturePreviewImage
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedPreview
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface ViewfinderCaptureDelegate {

    val captureEvents: Flow<CapturedImageEvent>

    fun bind(stateHolder: ViewfinderStateHolder)
    fun onScreenCreated(host: ViewfinderHost)
    fun onScreenDestroyed()

    fun takePicture()
    fun cancelPictureCapture()
    fun startPictureSave()
    fun finishPictureSave()

    fun takePreviewPicture()
    fun showCapturedPreview()
    fun confirmPreviewPicture(bitmap: Bitmap)
    fun dismissCapturedPreview()

    fun setSelfTimerRunning(running: Boolean)
    fun selfTimerCountdown(seconds: Int): Flow<Int>
}

internal class ViewfinderCaptureDelegateImpl @Inject constructor(
    private val captureImage: CaptureImage,
    private val capturePreviewImage: CapturePreviewImage,
    private val storeCapturedPreview: StoreCapturedPreview,
    private val capturedItemRepository: CapturedItemRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderCaptureDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    private var host: ViewfinderHost? = null
    private var pendingCapture: PendingCapture? = null

    private val events = Channel<CapturedImageEvent>(capacity = Channel.BUFFERED)
    override val captureEvents: Flow<CapturedImageEvent> = events.receiveAsFlow()

    override fun bind(stateHolder: ViewfinderStateHolder) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder
    }

    override fun onScreenCreated(host: ViewfinderHost) {
        this.host = host
    }

    override fun onScreenDestroyed() {
        host = null
        updateCapture { ViewfinderCaptureState() }
    }

    override fun takePicture() {
        val chrome = host?.chrome ?: return
        val state = stateHolder.state.value
        val thumbnailSize = chrome.thumbnailSize()
        val pending = PendingCapture()

        pendingCapture = pending

        updateCapture { it.copy(isTakingPicture = true) }

        applicationScope.launch(mainDispatcher) {
            try {
                captureImage(
                    request = CaptureImageRequest(
                        storageLocation = capturedItemRepository.storageLocation.first(),
                        includeLocation = state.requireLocation,
                        saveAsPreviewed = state.settings.saveImageAsPreviewed,
                        removeExif = state.settings.removeExifAfterCapture,
                        targetThumbnailWidth = thumbnailSize.width,
                        targetThumbnailHeight = thumbnailSize.height,
                    ),
                    needsThumbnail = { host != null },
                    onEvent = { event ->
                        onCapturedImageEvent(pending, event)
                    },
                )
            } finally {
                finish(pending)
            }
        }
    }

    override fun cancelPictureCapture() {
        val pending = pendingCapture ?: return

        pending.isCancelled = true
        finish(pending)
    }

    override fun startPictureSave() {
        updateCapture { it.copy(isSavingPicture = true) }
    }

    override fun finishPictureSave() {
        updateCapture { it.copy(isSavingPicture = false) }
    }

    override fun takePreviewPicture() {
        startPictureSave()

        applicationScope.launch(mainDispatcher) {
            val update = when (val result = capturePreviewImage()) {
                is CapturePreviewResult.Unavailable -> null
                is CapturePreviewResult.Failed -> CapturedImageEvent.PreviewFailed

                is CapturePreviewResult.Captured -> {
                    CapturedImageEvent.PreviewCaptured(bitmap = result.bitmap)
                }
            }

            update?.let { events.trySend(it) }
        }
    }

    override fun showCapturedPreview() {
        updateCapture { it.copy(isCapturedPreviewShown = true) }
    }

    override fun confirmPreviewPicture(bitmap: Bitmap) {
        val uri = host?.chrome?.foreignOutputUri()

        if (uri == null) {
            events.trySend(CapturedImageEvent.PreviewReturned)
            return
        }

        applicationScope.launch(mainDispatcher) {
            val event = when {
                storeCapturedPreview(uri = uri, bitmap = bitmap) -> {
                    CapturedImageEvent.PreviewStored
                }

                else -> CapturedImageEvent.PreviewStoreFailed
            }

            events.trySend(event)
        }
    }

    override fun dismissCapturedPreview() {
        updateCapture { it.copy(isCapturedPreviewShown = false) }
    }

    override fun setSelfTimerRunning(running: Boolean) {
        updateCapture { it.copy(isSelfTimerRunning = running) }
    }

    override fun selfTimerCountdown(seconds: Int): Flow<Int> {
        return flow {
            for (secondsLeft in seconds downTo 1) {
                emit(secondsLeft)
                delay(SELF_TIMER_TICK)
            }
        }
    }

    private fun onCapturedImageEvent(
        pending: PendingCapture,
        event: CapturedImageEvent,
    ) {
        when (event) {
            is CapturedImageEvent.Captured -> {
                finish(pending)
            }

            is CapturedImageEvent.CaptureFailed -> {
                finish(pending)

                if (pending.isCancelled) {
                    return
                }
            }

            else -> Unit
        }

        events.trySend(event)
    }

    private fun finish(pending: PendingCapture) {
        if (pendingCapture !== pending) {
            return
        }

        pendingCapture = null
        updateCapture { it.copy(isTakingPicture = false) }
    }

    private fun updateCapture(transform: (ViewfinderCaptureState) -> ViewfinderCaptureState) {
        stateHolder.update { it.copy(capture = transform(it.capture)) }
    }

    // Cancelling does not stop the request CameraX is already serving, so its failure
    // still arrives and has to be kept quiet.
    private class PendingCapture {
        var isCancelled = false
    }

    private companion object {
        private val SELF_TIMER_TICK = 1.seconds
    }
}
