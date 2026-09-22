package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.location.Location
import android.util.Log
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingRequest
import app.grapheneos.camera.data.camera.session.VideoRecordingSession
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.capture.model.RecordingOutput
import app.grapheneos.camera.domain.capture.usecase.CreateRecordingOutput
import app.grapheneos.camera.domain.capture.usecase.DiscardRecording
import app.grapheneos.camera.domain.capture.usecase.PublishRecording
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingUpdate
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface ViewfinderRecordingDelegate {

    val recordingUpdates: Flow<RecordingUpdate>

    fun bind(stateHolder: ViewfinderStateHolder)
    fun onScreenCreated(host: ViewfinderHost)
    fun onScreenDestroyed()

    fun requestRecording()
    fun prepareRecording(includeLocation: Boolean, includeAudio: Boolean)
    fun startPreparedRecording()
    fun requestStop()

    fun startRecording()
    fun markStopped()
    fun setRecordedDuration(duration: Duration)
    fun setPaused(paused: Boolean)
    fun setMuted(muted: Boolean)

    fun saveRecording()
    fun discardRecording()
}

internal class ViewfinderRecordingDelegateImpl @Inject constructor(
    private val recordingSession: VideoRecordingSession,
    private val createRecordingOutput: CreateRecordingOutput,
    private val publishRecording: PublishRecording,
    private val discardRecording: DiscardRecording,
    private val capturedItemRepository: CapturedItemRepository,
    private val locationRepository: LocationRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderRecordingDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    private var host: ViewfinderHost? = null
    private var pendingRecording: PendingRecording? = null

    private val updates = Channel<RecordingUpdate>(capacity = Channel.BUFFERED)
    override val recordingUpdates: Flow<RecordingUpdate> = updates.receiveAsFlow()

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
        update { ViewfinderRecordingState() }
    }

    override fun requestRecording() {
        update { ViewfinderRecordingState(phase = RecordingPhase.STARTING) }
    }

    override fun prepareRecording(includeLocation: Boolean, includeAudio: Boolean) {
        applicationScope.launch(mainDispatcher) {
            val output = createRecordingOutput(
                storageLocation = capturedItemRepository.storageLocation.first(),
                foreignUri = host?.chrome?.foreignOutputUri(),
            )

            if (output == null) {
                updates.trySend(RecordingUpdate.OutputUnavailable)
                return@launch
            }

            // A stop that arrives while the output is still being created finds nothing to stop,
            // so the start queued behind it has to be abandoned here instead.
            if (stateHolder.state.value.recording.phase == RecordingPhase.IDLE) {
                closeOutput(output)
                discard(output)
                return@launch
            }

            pendingRecording = PendingRecording(
                output = output,
                location = location(includeLocation),
                includeAudio = includeAudio,
            )

            updates.trySend(RecordingUpdate.ReadyToStart)
        }
    }

    override fun startPreparedRecording() {
        val pending = pendingRecording ?: return

        // The sound callback may fire more than once; a second start() throws.
        if (pending.isStarted) {
            return
        }

        pending.isStarted = true

        recordingSession.start(
            request = RecordingRequest(
                fileDescriptor = pending.output.fileDescriptor,
                location = pending.location,
                includeAudio = pending.includeAudio,
            ),
            onEvent = ::onRecordingEvent,
        )

        applyPendingControls()

        // FileDescriptorOutputOptions doc says that the file descriptor should be closed by
        // the caller, and that it's safe to do so as soon as the recording has started
        closeOutput(pending.output)
    }

    override fun requestStop() {
        val pending = pendingRecording

        when {
            pending == null -> Unit

            // The start is still queued behind the sound that announces it.
            !pending.isStarted -> {
                closeOutput(pending.output)
                discardRecording()
                updates.trySend(RecordingUpdate.Abandoned)
            }

            else -> recordingSession.stop()
        }
    }

    override fun startRecording() {
        update { it.copy(phase = RecordingPhase.RECORDING) }
    }

    override fun markStopped() {
        update { ViewfinderRecordingState() }
    }

    override fun setRecordedDuration(duration: Duration) {
        update { it.copy(duration = duration) }
    }

    // Not tied to the recording phase: the user may have paused before the recording actually
    // started.
    override fun setPaused(paused: Boolean) {
        update { it.copy(isPaused = paused) }
        recordingSession.setPaused(paused)
    }

    override fun setMuted(muted: Boolean) {
        update { it.copy(isMuted = muted) }
        recordingSession.setMuted(muted)
    }

    override fun saveRecording() {
        val pending = pendingRecording ?: return
        pendingRecording = null

        applicationScope.launch(mainDispatcher) {
            if (!publishRecording(pending.output)) {
                updates.trySend(RecordingUpdate.SaveFailed)
            }

            updates.trySend(
                RecordingUpdate.Saved(
                    uri = pending.output.uri,
                    item = capturedItem(pending.output),
                ),
            )
        }
    }

    override fun discardRecording() {
        val pending = pendingRecording ?: return
        pendingRecording = null

        discard(pending.output)
    }

    private fun onRecordingEvent(event: RecordingEvent) {
        when (event) {
            is RecordingEvent.Started -> updates.trySend(RecordingUpdate.Started)

            is RecordingEvent.Progressed -> {
                updates.trySend(
                    RecordingUpdate.Progressed(
                        duration = event.recordedDurationNanos.nanoseconds,
                    ),
                )
            }

            is RecordingEvent.Finalized -> {
                updates.trySend(RecordingUpdate.Finished(outcome = event.outcome))
            }
        }
    }

    private fun location(includeLocation: Boolean): Location? {
        if (!includeLocation) {
            return null
        }

        val location = locationRepository.currentLocation()
        if (location == null) {
            updates.trySend(RecordingUpdate.LocationUnavailable)
        }

        return location
    }

    // The Recording didn't exist yet when the mute/pause setters ran.
    private fun applyPendingControls() {
        val recording = stateHolder.state.value.recording

        if (recording.isMuted) {
            recordingSession.setMuted(true)
        }
        if (recording.isPaused) {
            recordingSession.setPaused(true)
        }
    }

    private fun capturedItem(output: RecordingOutput): CapturedItem? {
        return when {
            output.isOwnFile -> CapturedItem(ITEM_TYPE_VIDEO, output.dateString, output.uri)
            else -> null
        }
    }

    private fun discard(output: RecordingOutput) {
        applicationScope.launch(mainDispatcher) {
            discardRecording.invoke(output)
        }
    }

    private fun closeOutput(output: RecordingOutput) {
        try {
            output.fileDescriptor.close()
        } catch (e: IOException) {
            Log.w(TAG, "unable to close the recording output", e)
        }
    }

    private fun update(transform: (ViewfinderRecordingState) -> ViewfinderRecordingState) {
        stateHolder.update { it.copy(recording = transform(it.recording)) }
    }

    private class PendingRecording(
        val output: RecordingOutput,
        val location: Location?,
        val includeAudio: Boolean,
    ) {
        var isStarted = false
    }

    private companion object {
        private const val TAG = "ViewfinderRecording"
    }
}
