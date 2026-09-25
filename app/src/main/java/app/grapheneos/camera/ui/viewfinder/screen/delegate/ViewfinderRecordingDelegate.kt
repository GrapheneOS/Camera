package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.net.Uri
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.capture.coordinator.VideoRecorder
import app.grapheneos.camera.domain.capture.model.RecordVideoRequest
import app.grapheneos.camera.domain.capture.model.RecordedVideoEvent
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface ViewfinderRecordingDelegate {

    val recordingEvents: Flow<ViewfinderRecordingEvent>

    fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    )

    fun onScreenDestroyed()

    fun requestRecording()
    fun prepareRecording(
        includeLocation: Boolean,
        includeAudio: Boolean,
        outputUri: Uri?,
    )
    fun startPreparedRecording()

    fun setPaused(paused: Boolean)
    fun setMuted(muted: Boolean)

    fun requestStop()
    fun markStopped()

    fun retryOnceStreaming()
    fun takeRetry(): Boolean
}

internal class ViewfinderRecordingDelegateImpl @Inject constructor(
    private val videoRecorder: VideoRecorder,
    private val capturedItemRepository: CapturedItemRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderRecordingDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    private val _recordingEvents = Channel<ViewfinderRecordingEvent>(capacity = Channel.BUFFERED)
    override val recordingEvents: Flow<ViewfinderRecordingEvent> = _recordingEvents.receiveAsFlow()

    override fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    ) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder

        scope.launch(mainDispatcher) {
            videoRecorder.events.collect { event ->
                onRecordedVideoEvent(event)
            }
        }
    }

    override fun onScreenDestroyed() {
        update { ViewfinderRecordingState() }
    }

    override fun requestRecording() {
        update { recording ->
            ViewfinderRecordingState(
                phase = RecordingPhase.STARTING,
                isSaving = recording.isSaving,
                orientationAtStart = stateHolder.state.value.deviceOrientation,
            )
        }
    }

    override fun prepareRecording(
        includeLocation: Boolean,
        includeAudio: Boolean,
        outputUri: Uri?,
    ) {
        applicationScope.launch(mainDispatcher) {
            videoRecorder.prepare(
                request = RecordVideoRequest(
                    storageLocation = capturedItemRepository.storageLocation.first(),
                    foreignUri = outputUri,
                    includeLocation = includeLocation,
                    includeAudio = includeAudio,
                ),
                isStillWanted = { stateHolder.state.value.recording.isActive() },
            )
        }
    }

    override fun startPreparedRecording() {
        val recording = stateHolder.state.value.recording

        videoRecorder.start(
            muted = recording.isMuted,
            paused = recording.isPaused,
        )
    }

    // Not tied to the recording phase: the user may have paused before the recording actually
    // started.
    override fun setPaused(paused: Boolean) {
        update { it.copy(isPaused = paused) }
        videoRecorder.setPaused(paused)
    }

    override fun setMuted(muted: Boolean) {
        update { it.copy(isMuted = muted) }
        videoRecorder.setMuted(muted)
    }

    override fun requestStop() {
        videoRecorder.stop()
    }

    override fun markStopped() {
        update { recording -> ViewfinderRecordingState(isSaving = recording.isSaving) }
    }

    override fun retryOnceStreaming() {
        update { it.copy(retriesOnceStreaming = true) }
    }

    override fun takeRetry(): Boolean {
        val retries = stateHolder.state.value.recording.retriesOnceStreaming

        update { it.copy(retriesOnceStreaming = false) }

        return retries
    }

    private fun onRecordedVideoEvent(event: RecordedVideoEvent) {
        when (event) {
            is RecordedVideoEvent.OutputUnavailable -> onOutputUnavailable()
            is RecordedVideoEvent.Abandoned -> markStopped()
            is RecordedVideoEvent.Started -> update { it.copy(phase = RecordingPhase.RECORDING) }
            is RecordedVideoEvent.Finished -> onRecordingFinished(event.outcome)
            is RecordedVideoEvent.ReadyToStart -> postEffect(Effect.Recording.PlayStartSound)
            is RecordedVideoEvent.Progressed -> update { it.copy(duration = event.duration) }
            is RecordedVideoEvent.Saved -> onRecordingSaved(event)

            is RecordedVideoEvent.LocationUnavailable -> {
                postEffect(Effect.ShowMessage(R.string.location_unavailable))
            }

            is RecordedVideoEvent.SaveFailed -> {
                postEffect(Effect.ShowMessage(R.string.unable_to_save_video))
            }
        }
    }

    private fun onOutputUnavailable() {
        _recordingEvents.trySend(ViewfinderRecordingEvent.OutputUnavailable)

        postEffect(Effect.ShowMessage(R.string.unable_to_access_output_file))
        markStopped()
    }

    private fun onRecordingFinished(outcome: RecordingOutcome) {
        markStopped()

        if (outcome.keepsContent()) {
            update { it.copy(isSaving = true) }
        }

        when (outcome) {
            is RecordingOutcome.Saved -> Unit

            is RecordingOutcome.NothingPlayableWritten -> {
                postEffect(Effect.ShowMessage(R.string.recording_too_short_to_be_saved))
            }

            is RecordingOutcome.Failed -> {
                postEffect(Effect.Recording.SaveFailed(errorCode = outcome.errorCode))
            }

            is RecordingOutcome.Interrupted -> {
                postEffect(Effect.Recording.Interrupted(errorCode = outcome.errorCode))
            }
        }
    }

    private fun onRecordingSaved(event: RecordedVideoEvent.Saved) {
        update { it.copy(isSaving = false) }

        event.item?.let { item ->
            _recordingEvents.trySend(ViewfinderRecordingEvent.Saved(item))
        }

        if (stateHolder.state.value.isCaptureSession) {
            postEffect(Effect.Recording.ShowForReview(uri = event.uri))
        }
    }

    private fun postEffect(effect: Effect) {
        stateHolder.postEffect(effect)
    }

    private fun update(transform: (ViewfinderRecordingState) -> ViewfinderRecordingState) {
        stateHolder.update { it.copy(recording = transform(it.recording)) }
    }
}
