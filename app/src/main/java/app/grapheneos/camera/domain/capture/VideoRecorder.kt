package app.grapheneos.camera.domain.capture

import android.location.Location
import android.util.Log
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.data.camera.model.RecordingEvent
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

    suspend fun prepare(request: RecordVideoRequest, isStillWanted: () -> Boolean)

    fun start(muted: Boolean, paused: Boolean)

    fun setPaused(paused: Boolean)
    fun setMuted(muted: Boolean)

    fun stop()

    fun save()
    fun discard()
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
            onEvent = ::onRecordingEvent,
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
                closeOutput(pending.output)
                discard()
                _events.trySend(RecordedVideoEvent.Abandoned)
            }

            else -> recordingSession.stop()
        }
    }

    override fun save() {
        val pending = pendingRecording ?: return
        pendingRecording = null

        applicationScope.launch(mainDispatcher) {
            if (!publishRecording(pending.output)) {
                _events.trySend(RecordedVideoEvent.SaveFailed)
            }

            _events.trySend(
                RecordedVideoEvent.Saved(
                    uri = pending.output.uri,
                    item = capturedItem(pending.output),
                ),
            )
        }
    }

    override fun discard() {
        val pending = pendingRecording ?: return
        pendingRecording = null

        discardOutput(pending.output)
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

    private fun onRecordingEvent(event: RecordingEvent) {
        when (event) {
            is RecordingEvent.Started -> {
                _events.trySend(RecordedVideoEvent.Started)
            }

            is RecordingEvent.Progressed -> {
                _events.trySend(
                    RecordedVideoEvent.Progressed(
                        duration = event.recordedDurationNanos.nanoseconds,
                    ),
                )
            }

            is RecordingEvent.Finalized -> {
                _events.trySend(RecordedVideoEvent.Finished(outcome = event.outcome))
            }
        }
    }

    private fun capturedItem(output: RecordingOutput): CapturedItem? {
        return when {
            output.isOwnFile -> CapturedItem(ITEM_TYPE_VIDEO, output.dateString, output.uri)
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
