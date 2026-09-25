package app.grapheneos.camera.domain.capture.coordinator

import android.location.Location
import android.util.Log
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import app.grapheneos.camera.data.camera.model.RecordingRequest
import app.grapheneos.camera.data.camera.session.VideoRecordingSession
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
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface VideoRecorder {

    val events: Flow<RecordedVideoEvent>

    suspend fun prepare(
        request: RecordVideoRequest,
        isStillWanted: () -> Boolean,
    )

    fun start(muted: Boolean, paused: Boolean)
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
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : VideoRecorder {

    private var pendingRecording: PendingRecording? = null

    private val _events = Channel<RecordedVideoEvent>(capacity = Channel.BUFFERED)
    override val events: Flow<RecordedVideoEvent> = _events.receiveAsFlow()

    override suspend fun prepare(
        request: RecordVideoRequest,
        isStillWanted: () -> Boolean,
    ) {
        val output = createRecordingOutput(
            storageLocation = request.storageLocation,
            foreignUri = request.foreignUri,
        )

        if (output == null) {
            _events.trySend(RecordedVideoEvent.OutputUnavailable)
            return
        }

        // A stop that arrives while the output is still being created finds nothing to stop,
        // so the start queued behind it has to be abandoned here instead.
        if (!isStillWanted()) {
            closeOutput(output)
            discardOutput(output)
            return
        }

        pendingRecording = PendingRecording(
            output = output,
            location = location(request.includeLocation),
            includeAudio = request.includeAudio,
        )

        _events.trySend(RecordedVideoEvent.ReadyToStart)
    }

    override fun start(muted: Boolean, paused: Boolean) {
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
            onEvent = { event ->
                onRecordingEvent(
                    recording = pending,
                    event = event,
                )
            },
        )

        // The Recording didn't exist yet when the mute/pause setters ran.
        if (muted) {
            recordingSession.setMuted(true)
        }

        if (paused) {
            recordingSession.setPaused(true)
        }

        // FileDescriptorOutputOptions doc says that the file descriptor should be closed by
        // the caller, and that it's safe to do so as soon as the recording has started
        closeOutput(pending.output)
    }

    override fun setPaused(paused: Boolean) {
        recordingSession.setPaused(paused)
    }

    override fun setMuted(muted: Boolean) {
        recordingSession.setMuted(muted)
    }

    override fun stop() {
        val pending = pendingRecording

        when {
            pending == null -> Unit

            // The start is still queued behind the sound that announces it.
            !pending.isStarted -> {
                pendingRecording = null
                closeOutput(pending.output)
                discardOutput(pending.output)
                _events.trySend(RecordedVideoEvent.Abandoned)
            }

            else -> recordingSession.stop()
        }
    }

    private suspend fun location(includeLocation: Boolean): Location? {
        return when (val result = resolveCaptureLocation(includeLocation)) {
            is CaptureLocation.Found -> result.location
            is CaptureLocation.NotRequested -> null

            is CaptureLocation.Unavailable -> {
                _events.trySend(RecordedVideoEvent.LocationUnavailable)
                null
            }
        }
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
            is RecordingEvent.Started -> {
                RecordedVideoEvent.Started
            }

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

        when {
            keepsContent(outcome) -> save(recording.output)
            else -> discardOutput(recording.output)
        }
    }

    private fun keepsContent(outcome: RecordingOutcome): Boolean {
        return when (outcome) {
            is RecordingOutcome.Saved -> true
            is RecordingOutcome.Interrupted -> outcome.hasContent
            is RecordingOutcome.NothingPlayableWritten -> false
            is RecordingOutcome.Failed -> false
        }
    }

    private fun save(output: RecordingOutput) {
        applicationScope.launch(mainDispatcher) {
            if (!publishRecording(output)) {
                _events.trySend(RecordedVideoEvent.SaveFailed)
            }

            _events.trySend(
                RecordedVideoEvent.Saved(
                    uri = output.uri,
                    item = capturedItem(output),
                ),
            )
        }
    }

    private fun capturedItem(output: RecordingOutput): CapturedItem? {
        return when {
            output.isOwnFile -> {
                CapturedItem(
                    type = ITEM_TYPE_VIDEO,
                    dateString = output.dateString,
                    uri = output.uri
                )
            }

            else -> null
        }
    }

    private fun discardOutput(output: RecordingOutput) {
        applicationScope.launch(mainDispatcher) {
            discardRecording(output)
        }
    }

    private fun closeOutput(output: RecordingOutput) {
        try {
            output.fileDescriptor.close()
        } catch (e: IOException) {
            Log.w(TAG, "unable to close the recording output", e)
        }
    }

    private class PendingRecording(
        val output: RecordingOutput,
        val location: Location?,
        val includeAudio: Boolean,
    ) {
        var isStarted = false
    }

    private companion object {
        private const val TAG = "VideoRecorder"
    }
}
