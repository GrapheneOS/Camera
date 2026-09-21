package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.capture.model.CaptureImageRequest
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.usecase.CaptureImage
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
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

    fun bind(scope: CoroutineScope, stateHolder: ViewfinderStateHolder)
    fun onScreenCreated(host: ViewfinderHost)
    fun onScreenDestroyed()

    fun requestRecording()
    fun startRecording()
    fun setRecordingPaused(paused: Boolean)
    fun setRecordingMuted(muted: Boolean)
    fun stopRecording()

    fun takePicture()
    fun cancelPictureCapture()

    fun startPictureSave()
    fun finishPictureSave()

    fun setSelfTimerRunning(running: Boolean)
    fun selfTimerCountdown(seconds: Int): Flow<Int>

    fun showCapturedPreview()
    fun dismissCapturedPreview()
}

internal class ViewfinderCaptureDelegateImpl @Inject constructor(
    private val captureImage: CaptureImage,
    private val capturedItemRepository: CapturedItemRepository,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderCaptureDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder
    private lateinit var scope: CoroutineScope

    private var isBound = false

    private var host: ViewfinderHost? = null

    private var pendingCapture: PendingCapture? = null

    private val events = Channel<CapturedImageEvent>(capacity = Channel.BUFFERED)
    override val captureEvents: Flow<CapturedImageEvent> = events.receiveAsFlow()

    // Cancelling does not stop the request CameraX is already serving, so its failure
    // still arrives and has to be kept quiet.
    private class PendingCapture {
        var isCancelled = false
    }

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

    override fun onScreenDestroyed() {
        host = null
        stateHolder.update { it.copy(capture = ViewfinderCaptureState()) }
    }

    override fun takePicture() {
        val chrome = host?.chrome ?: return
        val state = stateHolder.state.value
        val thumbnailSize = chrome.thumbnailSize()
        val pending = PendingCapture()

        pendingCapture = pending

        updateCapture { it.copy(isTakingPicture = true) }

        scope.launch(mainDispatcher) {
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
        }
    }

    override fun cancelPictureCapture() {
        val pending = pendingCapture ?: return

        pending.isCancelled = true
        finish(pending)
    }

    private fun onCapturedImageEvent(
        pending: PendingCapture,
        event: CapturedImageEvent,
    ) {
        when (event) {
            is CapturedImageEvent.Captured -> finish(pending)

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
        if (pendingCapture === pending) {
            pendingCapture = null
        }
    }

    override fun requestRecording() {
        // Don't leak paused/muted state from the previous recording into this one.
        updateCapture {
            it.copy(
                recordingPhase = RecordingPhase.STARTING,
                isRecordingPaused = false,
                isRecordingMuted = false,
            )
        }
    }

    override fun startRecording() {
        updateCapture { it.copy(recordingPhase = RecordingPhase.RECORDING) }
    }

    // Not tied to the recording phase: the user may have paused before the recording actually
    // started.
    override fun setRecordingPaused(paused: Boolean) {
        updateCapture { it.copy(isRecordingPaused = paused) }
    }

    override fun setRecordingMuted(muted: Boolean) {
        updateCapture { it.copy(isRecordingMuted = muted) }
    }

    override fun stopRecording() {
        updateCapture {
            it.copy(
                recordingPhase = RecordingPhase.IDLE,
                isRecordingPaused = false,
                isRecordingMuted = false,
            )
        }
    }

    override fun startPictureSave() {
        updateCapture { it.copy(isSavingPicture = true) }
    }

    override fun finishPictureSave() {
        updateCapture { it.copy(isSavingPicture = false) }
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

    override fun showCapturedPreview() {
        updateCapture { it.copy(isCapturedPreviewShown = true) }
    }

    override fun dismissCapturedPreview() {
        updateCapture { it.copy(isCapturedPreviewShown = false) }
    }

    private fun updateCapture(transform: (ViewfinderCaptureState) -> ViewfinderCaptureState) {
        stateHolder.update { it.copy(capture = transform(it.capture)) }
    }

    private companion object {
        private val SELF_TIMER_TICK = 1.seconds
    }
}
