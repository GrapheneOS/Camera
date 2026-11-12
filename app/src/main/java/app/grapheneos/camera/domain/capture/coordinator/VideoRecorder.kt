package app.grapheneos.camera.domain.capture.coordinator

import android.location.Location
import android.util.Log
import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import app.grapheneos.camera.data.camera.model.RecordingRequest
import app.grapheneos.camera.data.camera.session.VideoRecordingSession
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.sound.model.CameraSound
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.capture.model.CaptureLocation
import app.grapheneos.camera.domain.capture.model.RecordVideoRequest
import app.grapheneos.camera.domain.capture.model.RecordedVideoEvent
import app.grapheneos.camera.domain.capture.model.RecordingOutput
import app.grapheneos.camera.domain.capture.usecase.CreateRecordingOutput
import app.grapheneos.camera.domain.capture.usecase.DiscardRecording
import app.grapheneos.camera.domain.capture.usecase.PublishRecording
import app.grapheneos.camera.domain.capture.usecase.ResolveCaptureLocation
import app.grapheneos.camera.domain.sound.usecase.PlayCameraSound
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface VideoRecorder {

    val events: Flow<RecordedVideoEvent>

    suspend fun prepare(
        request: RecordVideoRequest,
        isStillWanted: () -> Boolean,
    )

    /** Returns the recording ID to pass to [onStartSoundPlayed], or null if it did not start. */
    fun startPaused(includeAudio: Boolean): Int?

    fun onStartSoundPlayed(
        recordingId: Int,
        paused: Boolean,
        muted: Boolean,
    )

    fun setPaused(paused: Boolean)

    fun setMuted(muted: Boolean)

    fun stop()
}

internal class VideoRecorderImpl @Inject constructor(
    private val recordingSession: VideoRecordingSession,
    private val createRecordingOutput: CreateRecordingOutput,
    private val publishRecording: PublishRecording,
    private val discardRecording: DiscardRecording,
    private val resolveCaptureLocation: ResolveCaptureLocation,
    private val playCameraSound: PlayCameraSound,
    private val capturedItemRepository: CapturedItemRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : VideoRecorder {

    private var pendingRecording: PendingRecording? = null

    private var lastRecordingId = 0
    private var preparingRecordingId: Int? = null

    private val _events = Channel<RecordedVideoEvent>(capacity = Channel.BUFFERED)
    override val events: Flow<RecordedVideoEvent> = _events.receiveAsFlow()

    override suspend fun prepare(
        request: RecordVideoRequest,
        isStillWanted: () -> Boolean,
    ) {
        val recordingId = ++lastRecordingId
        preparingRecordingId = recordingId

        val storageLocation = capturedItemRepository.storageLocation.first()
        if (recordingId != preparingRecordingId) return

        val output = createRecordingOutput(
            storageLocation = storageLocation,
            foreignUri = request.foreignUri,
        )
        val location = output?.let { resolveCaptureLocation(request.includeLocation) }
        val isCurrent = recordingId == preparingRecordingId

        if (isCurrent) {
            preparingRecordingId = null
        }

        when {
            !isCurrent || !isStillWanted() -> output?.let { discard(it) }

            output == null -> _events.trySend(RecordedVideoEvent.OutputUnavailable)

            else -> {
                if (location == CaptureLocation.Unavailable) {
                    _events.trySend(RecordedVideoEvent.LocationUnavailable)
                }
                pendingRecording = PendingRecording(
                    id = recordingId,
                    output = output,
                    location = (location as? CaptureLocation.Found)?.location,
                )
                _events.trySend(RecordedVideoEvent.ReadyToStart)
            }
        }
    }

    override fun startPaused(includeAudio: Boolean): Int? {
        val pending = pendingRecording?.takeIf { it.phase == Phase.PREPARED } ?: return null

        val isSessionStarted = recordingSession.start(
            request = RecordingRequest(
                fileDescriptor = pending.output.fileDescriptor,
                location = pending.location,
                includeAudio = includeAudio,
            ),
            onEvent = { event ->
                onRecordingEvent(
                    recording = pending,
                    event = event,
                )
            },
        )

        return when {
            isSessionStarted -> {
                pending.phase = Phase.WAITING_FOR_SOUND
                recordingSession.setPaused(true)
                // CameraX duplicates the output descriptor when the recording starts.
                closeOutput(pending.output)
                pending.id
            }

            else -> {
                abandon(pending)
                null
            }
        }
    }

    override fun onStartSoundPlayed(
        recordingId: Int,
        paused: Boolean,
        muted: Boolean,
    ) {
        val pending = pendingRecording ?: return
        if (pending.id != recordingId || pending.phase != Phase.WAITING_FOR_SOUND) return

        pending.phase = Phase.STARTED
        recordingSession.setMuted(muted)

        if (!paused) {
            recordingSession.setPaused(false)
        }
    }

    override fun setPaused(paused: Boolean) {
        if (pendingRecording?.phase == Phase.STARTED) {
            recordingSession.setPaused(paused)
        }
    }

    override fun setMuted(muted: Boolean) {
        if (pendingRecording?.phase == Phase.STARTED) {
            recordingSession.setMuted(muted)
        }
    }

    override fun stop() {
        val pending = pendingRecording

        when {
            pending == null -> abandonPreparation()
            pending.phase == Phase.PREPARED -> abandon(pending)
            pending.phase == Phase.WAITING_FOR_SOUND -> cancelBeforeStartSoundEnds(pending)
            else -> recordingSession.stop()
        }
    }

    private fun cancelBeforeStartSoundEnds(pending: PendingRecording) {
        pending.phase = Phase.CANCELLED
        pendingRecording = null
        recordingSession.stop()
        _events.trySend(RecordedVideoEvent.Abandoned)
    }

    private fun abandon(pending: PendingRecording) {
        pendingRecording = null
        discard(pending.output)
        _events.trySend(RecordedVideoEvent.Abandoned)
    }

    private fun onRecordingEvent(
        recording: PendingRecording,
        event: RecordingEvent,
    ) {
        if (recording === pendingRecording) {
            _events.trySend(recordedVideoEvent(event))
        }

        if (event is RecordingEvent.Finalized) {
            finish(
                recording = recording,
                outcome = event.outcome,
            )
        }
    }

    private fun recordedVideoEvent(event: RecordingEvent): RecordedVideoEvent {
        return when (event) {
            is RecordingEvent.Started -> RecordedVideoEvent.Started

            is RecordingEvent.Progressed -> {
                RecordedVideoEvent.Progressed(duration = event.recordedDurationNanos.nanoseconds)
            }

            is RecordingEvent.Finalized -> {
                RecordedVideoEvent.Finished(outcome = event.outcome)
            }
        }
    }

    private fun finish(
        recording: PendingRecording,
        outcome: RecordingOutcome,
    ) {
        if (recording === pendingRecording) {
            pendingRecording = null
        }

        if (recording.phase == Phase.CANCELLED) {
            discardOutput(recording.output)
            return
        }

        applicationScope.launch(mainDispatcher) {
            playCameraSound(CameraSound.RECORDING_STOP)
        }

        if (outcome.keepsContent()) {
            save(recording.output)
        } else {
            discardOutput(recording.output)
        }
    }

    private fun abandonPreparation() {
        if (preparingRecordingId == null) return

        preparingRecordingId = null
        _events.trySend(RecordedVideoEvent.Abandoned)
    }

    private fun save(output: RecordingOutput) {
        applicationScope.launch(mainDispatcher) {
            if (!publishRecording(output)) {
                _events.trySend(RecordedVideoEvent.SaveFailed)
                return@launch
            }

            val item = capturedItem(output)

            _events.trySend(
                RecordedVideoEvent.Saved(
                    uri = output.uri,
                    item = item,
                ),
            )

            item?.let { storeLastCapturedItem(it) }
        }
    }

    private suspend fun storeLastCapturedItem(item: CapturedItem) {
        try {
            capturedItemRepository.saveLastCapturedItem(item)
        } catch (e: IOException) {
            Log.e(TAG, "unable to store the last captured item", e)
        }
    }

    private fun capturedItem(output: RecordingOutput): CapturedItem? {
        if (!output.isOwnFile) return null

        return CapturedItem(
            type = CapturedItemType.VIDEO,
            dateString = output.dateString,
            uri = output.uri,
        )
    }

    private fun discardOutput(output: RecordingOutput) {
        applicationScope.launch(mainDispatcher) {
            discardRecording(output)
        }
    }

    private fun discard(output: RecordingOutput) {
        closeOutput(output)
        discardOutput(output)
    }

    private fun closeOutput(output: RecordingOutput) {
        try {
            output.fileDescriptor.close()
        } catch (e: IOException) {
            Log.w(TAG, "unable to close the recording output", e)
        }
    }

    private enum class Phase {
        PREPARED,
        WAITING_FOR_SOUND,
        STARTED,
        CANCELLED,
    }

    private class PendingRecording(
        val id: Int,
        val output: RecordingOutput,
        val location: Location?,
    ) {
        var phase = Phase.PREPARED
    }

    private companion object {
        private const val TAG = "VideoRecorder"
    }
}
