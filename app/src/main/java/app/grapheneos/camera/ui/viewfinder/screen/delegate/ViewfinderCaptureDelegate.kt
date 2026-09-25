package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.capture.model.CaptureImageRequest
import app.grapheneos.camera.domain.capture.model.CapturePreviewResult
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.usecase.CaptureImage
import app.grapheneos.camera.domain.capture.usecase.CapturePreviewImage
import app.grapheneos.camera.domain.capture.usecase.NotifyPictureSaveFailed
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedPreview
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.PictureFailureDetails
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.util.printStackTraceToString
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface ViewfinderCaptureDelegate {

    val captureEvents: Flow<ViewfinderCaptureEvent>

    fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    )

    fun onScreenCreated(host: ViewfinderHost)
    fun onScreenStarted()
    fun onScreenStopped()
    fun onScreenDestroyed()

    fun takePicture()
    fun cancelPictureCapture()

    fun takePreviewPicture()
    fun showCapturedPreview()
    fun confirmPreviewPicture(bitmap: Bitmap, outputUri: Uri?)
    fun dismissCapturedPreview()

    fun startSelfTimer()
    fun cancelSelfTimer()
}

internal class ViewfinderCaptureDelegateImpl @Inject constructor(
    private val captureImage: CaptureImage,
    private val capturePreviewImage: CapturePreviewImage,
    private val storeCapturedPreview: StoreCapturedPreview,
    private val capturedItemRepository: CapturedItemRepository,
    private val notifyPictureSaveFailed: NotifyPictureSaveFailed,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderCaptureDelegate {

    private lateinit var scope: CoroutineScope

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    private var host: ViewfinderHost? = null
    private var isScreenStarted = false
    private var pendingCapture: PendingCapture? = null
    private var selfTimer: Job? = null

    private val _captureEvents = Channel<ViewfinderCaptureEvent>(capacity = Channel.BUFFERED)
    override val captureEvents: Flow<ViewfinderCaptureEvent> = _captureEvents.receiveAsFlow()

    override fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    ) {
        if (isBound) return
        isBound = true

        this.scope = scope
        this.stateHolder = stateHolder
    }

    override fun onScreenCreated(host: ViewfinderHost) {
        this.host = host
    }

    override fun onScreenStarted() {
        isScreenStarted = true
    }

    override fun onScreenStopped() {
        isScreenStarted = false
    }

    override fun onScreenDestroyed() {
        selfTimer?.cancel()
        host = null
        updateCapture { ViewfinderCaptureState() }
    }

    override fun takePicture() {
        val thumbnailSize = host?.thumbnailSize ?: return
        val state = stateHolder.state.value
        val pending = PendingCapture()

        pendingCapture = pending

        updateCapture { it.copy(isTakingPicture = true) }

        applicationScope.launch(mainDispatcher) {
            try {
                val storageLocation = capturedItemRepository.storageLocation.first()

                if (!pending.isCancelled) {
                    captureImage(
                        request = CaptureImageRequest(
                            storageLocation = storageLocation,
                            includeLocation = state.requireLocation,
                            saveAsPreviewed = state.settings.saveImageAsPreviewed,
                            removeExif = state.settings.removeExifAfterCapture,
                            targetThumbnailWidth = thumbnailSize.width,
                            targetThumbnailHeight = thumbnailSize.height,
                        ),
                        needsThumbnail = { host != null },
                        onEvent = { event ->
                            onPendingCaptureEvent(pending, event)
                        },
                    )
                }
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

            update?.let(::onCapturedImageEvent)
        }
    }

    override fun showCapturedPreview() {
        updateCapture { it.copy(isCapturedPreviewShown = true) }
    }

    override fun confirmPreviewPicture(
        bitmap: Bitmap,
        outputUri: Uri?,
    ) {
        if (outputUri == null) {
            onCapturedImageEvent(CapturedImageEvent.PreviewReturned(bitmap = bitmap))
            return
        }

        applicationScope.launch(mainDispatcher) {
            val event = when {
                storeCapturedPreview(uri = outputUri, bitmap = bitmap) -> {
                    CapturedImageEvent.PreviewStored
                }

                else -> CapturedImageEvent.PreviewStoreFailed
            }

            onCapturedImageEvent(event)
        }
    }

    override fun dismissCapturedPreview() {
        updateCapture { it.copy(isCapturedPreviewShown = false) }
    }

    override fun startSelfTimer() {
        cancelSelfTimer()

        postEffect(Effect.SelfTimer.Started)
        setSelfTimerRunning(true)

        val seconds = stateHolder.state.value.settings.selfTimerDurationSeconds
        selfTimer = scope.launch(mainDispatcher) {
            selfTimerCountdown(seconds).collect { secondsLeft ->
                postEffect(Effect.SelfTimer.Ticked(secondsLeft))
            }

            setSelfTimerRunning(false)
            postEffect(Effect.SelfTimer.Finished)
        }
    }

    override fun cancelSelfTimer() {
        // Cancelling puts back the controls the countdown hid. Doing that when no countdown is up
        // would resurrect the ones the current mode hid for its own reasons: QR mode hides
        // thirdOption and cancelButtonView, and the badge stays hidden with no timer set.
        if (selfTimer?.isActive != true) return

        selfTimer?.cancel()
        setSelfTimerRunning(false)
        postEffect(Effect.SelfTimer.Cancelled)
    }

    private fun setSelfTimerRunning(running: Boolean) {
        updateCapture { it.copy(isSelfTimerRunning = running) }
    }

    private fun selfTimerCountdown(seconds: Int): Flow<Int> {
        return flow {
            for (secondsLeft in seconds downTo 1) {
                emit(secondsLeft)
                delay(SELF_TIMER_TICK)
            }
        }
    }

    private fun storeLastCapturedItem(item: CapturedItem) {
        applicationScope.launch(mainDispatcher) {
            try {
                capturedItemRepository.saveLastCapturedItem(item)
            } catch (e: IOException) {
                Log.e(TAG, "unable to store the last captured item", e)
            }
        }
    }

    private fun onPendingCaptureEvent(
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

        onCapturedImageEvent(event)
    }

    private fun finish(pending: PendingCapture) {
        if (pendingCapture !== pending) {
            return
        }

        pendingCapture = null
        updateCapture { it.copy(isTakingPicture = false) }
    }

    private fun onCapturedImageEvent(event: CapturedImageEvent) {
        when (event) {
            is CapturedImageEvent.Captured -> onPictureCaptured()
            is CapturedImageEvent.ThumbnailReady -> onPictureThumbnailReady(event.thumbnail)
            is CapturedImageEvent.CaptureFailed -> onPictureCaptureFailed(event)
            is CapturedImageEvent.SaveFailed -> onPictureSaveFailed(event)
            is CapturedImageEvent.PreviewCaptured -> onPreviewCaptured(event.bitmap)
            is CapturedImageEvent.PreviewFailed -> onPreviewFailed()
            is CapturedImageEvent.PreviewStored -> postEffect(Effect.Picture.PreviewStored)

            is CapturedImageEvent.PreviewStoreFailed -> {
                postEffect(Effect.Picture.PreviewStoreFailed)
            }

            is CapturedImageEvent.StorageLocationNotFound -> {
                _captureEvents.trySend(ViewfinderCaptureEvent.StorageLocationNotFound)
            }

            is CapturedImageEvent.Saved -> {
                storeLastCapturedItem(event.item)
                _captureEvents.trySend(ViewfinderCaptureEvent.Saved(event.item))
            }

            is CapturedImageEvent.PreviewReturned -> {
                postEffect(Effect.Picture.PreviewReturned(bitmap = event.bitmap))
            }

            is CapturedImageEvent.LocationUnavailable -> {
                postEffect(Effect.ShowMessage(R.string.location_unavailable))
            }
        }
    }

    private fun onPictureCaptured() {
        startPictureSave()

        postEffect(Effect.Picture.Captured)
        postEffect(Effect.FlashPreview(stateHolder.state.value.selfIlluminate()))
    }

    private fun onPictureThumbnailReady(thumbnail: Bitmap) {
        finishPictureSave()

        _captureEvents.trySend(ViewfinderCaptureEvent.ThumbnailReady(thumbnail))
    }

    private fun onPictureCaptureFailed(event: CapturedImageEvent.CaptureFailed) {
        Log.e(TAG, "unable to capture a picture", event.cause)

        finishPictureSave()

        if (!isScreenStarted) return

        postEffect(
            Effect.Picture.CaptureFailed(
                errorCode = event.errorCode,
                details = detailsOf(event.cause),
            ),
        )
    }

    private fun onPictureSaveFailed(event: CapturedImageEvent.SaveFailed) {
        Log.e(TAG, "unable to save a picture", event.cause)

        finishPictureSave()

        when {
            isScreenStarted -> {
                postEffect(
                    Effect.Picture.SaveFailed(
                        stage = event.cause.place.name,
                        details = detailsOf(event.cause),
                        alreadyReported = event.alreadyReported,
                    ),
                )
            }

            else -> {
                applicationScope.launch(mainDispatcher) {
                    notifyPictureSaveFailed()
                }
            }
        }
    }

    private fun detailsOf(exception: Throwable): PictureFailureDetails {
        return PictureFailureDetails(
            name = exception.javaClass.name,
            stackTrace = exception.printStackTraceToString(),
        )
    }

    private fun onPreviewCaptured(bitmap: Bitmap) {
        finishPictureSave()

        postEffect(Effect.Picture.PreviewCaptured(bitmap = bitmap))
        postEffect(Effect.ShowMessage(R.string.image_captured_successfully))
    }

    private fun onPreviewFailed() {
        finishPictureSave()

        postEffect(Effect.Picture.PreviewFailed)
    }

    private fun startPictureSave() {
        updateCapture { it.copy(isSavingPicture = true) }
    }

    private fun finishPictureSave() {
        updateCapture { it.copy(isSavingPicture = false) }
    }

    private fun postEffect(effect: Effect) {
        stateHolder.postEffect(effect)
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
        private const val TAG = "ViewfinderCapture"

        private val SELF_TIMER_TICK = 1.seconds
    }
}
