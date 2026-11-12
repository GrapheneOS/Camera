package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.net.Uri
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.RecordingOutcome
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
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface ViewfinderRecordingDelegate {

    val recordingEvents: Flow<ViewfinderRecordingEvent>

    fun bind(stateHolder: ViewfinderStateHolder)

    suspend fun trackRecorder()

    fun onScreenDestroyed()

    fun requestRecording()
    fun prepareRecording(
        includeLocation: Boolean,
        outputUri: Uri?,
    )
    fun startPreparedRecording()
    fun onStartSoundPlayed(recordingId: Int)

    fun setPaused(paused: Boolean)
    fun toggleMute()

    fun requestStop()
    fun markStopped()

    fun retryOnceStreaming()
    fun takeRetry(): Boolean
}

internal class ViewfinderRecordingDelegateImpl @Inject constructor(
    private val videoRecorder: VideoRecorder,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderRecordingDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private val state: ViewfinderState
        get() = stateHolder.state.value

    private val _recordingEvents = Channel<ViewfinderRecordingEvent>(capacity = Channel.BUFFERED)
    override val recordingEvents: Flow<ViewfinderRecordingEvent> = _recordingEvents.receiveAsFlow()

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder
    }

    override suspend fun trackRecorder() {
        videoRecorder.events.collect { event ->
            onRecordedVideoEvent(event)
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
                orientationAtStart = state.deviceOrientation,
            )
        }
    }

    override fun prepareRecording(
        includeLocation: Boolean,
        outputUri: Uri?,
    ) {
        applicationScope.launch(mainDispatcher) {
            videoRecorder.prepare(
                request = RecordVideoRequest(
                    foreignUri = outputUri,
                    includeLocation = includeLocation,
                ),
                isStillWanted = { state.recording.isActive() },
            )
        }
    }

    override fun startPreparedRecording() {
        val recordingId = videoRecorder.startPaused(
            includeAudio = state.settings.includeAudio,
        ) ?: return

        postEffect(Effect.Recording.PlayStartSound(recordingId = recordingId))
    }

    override fun onStartSoundPlayed(recordingId: Int) {
        val recording = state.recording
        videoRecorder.onStartSoundPlayed(
            recordingId = recordingId,
            paused = recording.isPaused,
            muted = recording.isMuted,
        )
    }

    // Not tied to the recording phase: the user may have paused before the recording actually
    // started.
    override fun setPaused(paused: Boolean) {
        update { it.copy(isPaused = paused) }
        videoRecorder.setPaused(paused)
    }

    override fun toggleMute() {
        val recording = state.recording
        if (!recording.isActive()) return

        val muted = !recording.isMuted
        update { it.copy(isMuted = muted) }
        videoRecorder.setMuted(muted)

        postEffect(
            Effect.ShowMessage(
                when {
                    muted -> R.string.video_audio_recording_muted
                    else -> R.string.video_audio_recording_unmuted
                },
            ),
        )
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
        val retries = state.recording.retriesOnceStreaming

        update { it.copy(retriesOnceStreaming = false) }

        return retries
    }

    private fun onRecordedVideoEvent(event: RecordedVideoEvent) {
        when (event) {
            is RecordedVideoEvent.OutputUnavailable -> onOutputUnavailable()
            is RecordedVideoEvent.Abandoned -> markStopped()
            is RecordedVideoEvent.Started -> update { it.copy(phase = RecordingPhase.RECORDING) }
            is RecordedVideoEvent.Finished -> onRecordingFinished(event.outcome)
            is RecordedVideoEvent.Progressed -> update { it.copy(duration = event.duration) }
            is RecordedVideoEvent.Saved -> onRecordingSaved(event)

            is RecordedVideoEvent.ReadyToStart -> {
                _recordingEvents.trySend(ViewfinderRecordingEvent.ReadyToStart)
            }

            is RecordedVideoEvent.LocationUnavailable -> {
                postEffect(Effect.ShowMessage(R.string.location_unavailable))
            }

            is RecordedVideoEvent.SaveFailed -> {
                update { it.copy(isSaving = false) }
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

        if (state.isCaptureSession && !state.recording.isActive()) {
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
