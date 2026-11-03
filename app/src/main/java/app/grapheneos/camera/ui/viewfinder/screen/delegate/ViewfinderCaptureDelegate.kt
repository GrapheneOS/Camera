package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.graphics.scale
import app.grapheneos.camera.BuildConfig
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.sound.model.CameraSound
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.DefaultDispatcher
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.camera.usecase.BlinkTimerLight
import app.grapheneos.camera.domain.capture.model.CaptureImageRequest
import app.grapheneos.camera.domain.capture.model.CapturePreviewResult
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.usecase.CaptureImage
import app.grapheneos.camera.domain.capture.usecase.CapturePreviewImage
import app.grapheneos.camera.domain.capture.usecase.NotifyPictureSaveFailed
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedPreview
import app.grapheneos.camera.domain.sound.usecase.PlayCameraSound
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.PictureFailureCause
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderDialog
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderCaptureEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderCaptureResult
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderCaptureState
import app.grapheneos.camera.util.printStackTraceToString
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration
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
import kotlinx.coroutines.withContext

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

    fun holdShutter()

    fun takePicture()
    fun takePreviewPicture()
    fun cancelPictureCapture()

    fun showResult(result: ViewfinderCaptureResult)
    fun confirmResult(outputUri: Uri?)
    fun playResult()
    fun discardResult()

    fun startSelfTimer()
    fun cancelSelfTimer()
}

internal class ViewfinderCaptureDelegateImpl @Inject constructor(
    private val captureImage: CaptureImage,
    private val capturePreviewImage: CapturePreviewImage,
    private val storeCapturedPreview: StoreCapturedPreview,
    private val capturedItemRepository: CapturedItemRepository,
    private val notifyPictureSaveFailed: NotifyPictureSaveFailed,
    private val playCameraSound: PlayCameraSound,
    private val blinkTimerLight: BlinkTimerLight,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewfinderCaptureDelegate {

    private lateinit var scope: CoroutineScope

    private lateinit var stateHolder: ViewfinderStateHolder

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
        updateCapture { capture ->
            ViewfinderCaptureState(
                result = capture.result,
                isShutterHeld = capture.isShutterHeld,
            )
        }
    }

    override fun holdShutter() {
        updateCapture { it.copy(isShutterHeld = true) }

        scope.launch(mainDispatcher) {
            delay(SHUTTER_HOLD)
            updateCapture { it.copy(isShutterHeld = false) }
        }
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
                            includeLocation = state.includesLocation(),
                            saveAsPreviewed = state.settings.keepSelfiesAsSeen,
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

    override fun takePreviewPicture() {
        startPictureSave()

        applicationScope.launch(mainDispatcher) {
            when (val result = capturePreviewImage()) {
                is CapturePreviewResult.Unavailable -> Unit
                is CapturePreviewResult.Failed -> onPreviewFailed()
                is CapturePreviewResult.Captured -> onPreviewCaptured(result.bitmap)
            }
        }
    }

    override fun cancelPictureCapture() {
        val pending = pendingCapture ?: return

        pending.isCancelled = true
        finish(pending)
    }

    override fun showResult(result: ViewfinderCaptureResult) {
        updateCapture { it.copy(result = result) }
    }

    override fun confirmResult(outputUri: Uri?) {
        when (val result = stateHolder.state.value.capture.result) {
            null -> Unit

            is ViewfinderCaptureResult.Photo -> {
                returnPhoto(photo = result.image, outputUri = outputUri)
            }

            is ViewfinderCaptureResult.Video -> {
                postEffect(Effect.CaptureResult.VideoReturned(uri = result.uri))
            }
        }
    }

    override fun playResult() {
        val video = stateHolder.state.value.capture.result as? ViewfinderCaptureResult.Video

        video?.let { postEffect(Effect.CaptureResult.OpenVideo(uri = it.uri)) }
    }

    override fun discardResult() {
        updateCapture { it.copy(result = null) }
    }

    override fun startSelfTimer() {
        if (stateHolder.state.value.capture.isShutterHeld) return

        cancelSelfTimer()

        val settings = stateHolder.state.value.settings
        setSelfTimerRemaining(settings.selfTimerDuration)

        selfTimer = scope.launch(mainDispatcher) {
            val timer = this

            selfTimerCountdown(settings.selfTimerDuration).collect { secondsLeft ->
                setSelfTimerRemaining(secondsLeft.seconds)
                playCameraSound(tickSound(secondsLeft))

                val flashes = timerLightFlashes(secondsLeft)
                if (settings.timerLight && flashes > 0) {
                    timer.launch(mainDispatcher) { blinkTimerLight(flashes) }
                }
            }

            setSelfTimerRemaining(null)
            _captureEvents.trySend(ViewfinderCaptureEvent.SelfTimerFinished)
        }
    }

    override fun cancelSelfTimer() {
        // Cancelling puts back the controls the countdown hid. Doing that when no countdown is up
        // would resurrect the ones the current mode hid for its own reasons: QR mode hides
        // thirdOption and cancelButtonView, and the badge stays hidden with no timer set.
        if (selfTimer?.isActive != true) return

        selfTimer?.cancel()
        setSelfTimerRemaining(null)
    }

    private fun setSelfTimerRemaining(remaining: Duration?) {
        updateCapture { it.copy(selfTimerRemaining = remaining) }
    }

    private fun tickSound(secondsLeft: Int): CameraSound {
        return when (secondsLeft) {
            1 -> CameraSound.TIMER_FINAL_SECOND
            else -> CameraSound.TIMER_TICK
        }
    }

    // The last second stays dark.
    private fun timerLightFlashes(secondsLeft: Int): Int {
        return when {
            secondsLeft == 1 -> 0
            secondsLeft <= TIMER_LIGHT_FINAL_SECONDS -> 2
            else -> 1
        }
    }

    private fun selfTimerCountdown(duration: Duration): Flow<Int> {
        return flow {
            for (secondsLeft in duration.inWholeSeconds.toInt() downTo 1) {
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

            is CapturedImageEvent.StorageLocationNotFound -> {
                _captureEvents.trySend(ViewfinderCaptureEvent.StorageLocationNotFound)
            }

            is CapturedImageEvent.Saved -> {
                storeLastCapturedItem(event.item)
                _captureEvents.trySend(ViewfinderCaptureEvent.Saved(event.item))
            }

            is CapturedImageEvent.LocationUnavailable -> {
                postEffect(Effect.ShowMessage(R.string.location_unavailable))
            }
        }
    }

    private fun onPictureCaptured() {
        startPictureSave()

        applicationScope.launch(mainDispatcher) {
            playCameraSound(CameraSound.SHUTTER)
        }

        postEffect(Effect.Preview.Flash(stateHolder.state.value.selfIlluminates()))
    }

    private fun onPictureThumbnailReady(thumbnail: Bitmap?) {
        finishPictureSave()

        _captureEvents.trySend(ViewfinderCaptureEvent.ThumbnailReady(thumbnail))
    }

    private fun onPictureCaptureFailed(event: CapturedImageEvent.CaptureFailed) {
        Log.e(TAG, "unable to capture a picture", event.cause)

        finishPictureSave()

        if (!isScreenStarted) return

        showDialog(
            ViewfinderDialog.PictureFailure(
                cause = PictureFailureCause.Capture(errorCode = event.errorCode),
                details = failureDetails(event.cause),
            ),
        )
    }

    private fun onPictureSaveFailed(event: CapturedImageEvent.SaveFailed) {
        Log.e(TAG, "unable to save a picture", event.cause)

        finishPictureSave()

        when {
            !isScreenStarted -> {
                applicationScope.launch(mainDispatcher) {
                    notifyPictureSaveFailed()
                }
            }

            event.alreadyReported -> {
                postEffect(Effect.ShowMessage(R.string.unable_to_save_image))
            }

            else -> {
                showDialog(
                    ViewfinderDialog.PictureFailure(
                        cause = PictureFailureCause.Save(stage = event.cause.place.name),
                        details = failureDetails(event.cause),
                    ),
                )
            }
        }
    }

    private fun onPreviewCaptured(bitmap: Bitmap) {
        finishPictureSave()

        _captureEvents.trySend(ViewfinderCaptureEvent.PhotoCaptured(photo = bitmap))
        postEffect(Effect.ShowMessage(R.string.image_captured_successfully))
    }

    private fun onPreviewFailed() {
        finishPictureSave()

        postEffect(Effect.CaptureResult.PhotoFailed)
    }

    private fun returnPhoto(
        photo: Bitmap,
        outputUri: Uri?,
    ) {
        applicationScope.launch(mainDispatcher) {
            val effect = when {
                outputUri == null -> {
                    Effect.CaptureResult.PhotoReturned(bitmap = inlineResult(photo))
                }

                storeCapturedPreview(uri = outputUri, bitmap = photo) -> {
                    Effect.CaptureResult.PhotoStored
                }

                else -> Effect.CaptureResult.PhotoStoreFailed
            }

            postEffect(effect)
        }
    }

    // The photo goes back inside the result intent, and a binder transaction has no room for a
    // full-size bitmap.
    private suspend fun inlineResult(photo: Bitmap): Bitmap {
        if (photo.byteCount <= MAX_INLINE_RESULT_BYTES) return photo

        return withContext(defaultDispatcher) {
            photo.scale(
                photo.width / INLINE_RESULT_DOWNSCALE,
                photo.height / INLINE_RESULT_DOWNSCALE,
                false,
            )
        }
    }

    private fun startPictureSave() {
        updateCapture { it.copy(isSavingPicture = true) }
    }

    private fun finishPictureSave() {
        updateCapture { it.copy(isSavingPicture = false) }
    }

    private fun failureDetails(cause: Throwable): String {
        return "osVersion: ${Build.FINGERPRINT}" +
            "\npackage: ${BuildConfig.APPLICATION_ID}:${BuildConfig.VERSION_CODE}" +
            "\n\n${cause.printStackTraceToString()}"
    }

    private fun showDialog(dialog: ViewfinderDialog) {
        stateHolder.update { it.copy(dialog = dialog) }
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

        private const val TIMER_LIGHT_FINAL_SECONDS = 3
        private const val MAX_INLINE_RESULT_BYTES = 1_000_000
        private const val INLINE_RESULT_DOWNSCALE = 10

        private val SELF_TIMER_TICK = 1.seconds
        private val SHUTTER_HOLD = 1.seconds
    }
}
