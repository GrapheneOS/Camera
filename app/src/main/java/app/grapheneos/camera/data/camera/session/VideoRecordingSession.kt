package app.grapheneos.camera.data.camera.session

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.Recording
import app.grapheneos.camera.data.camera.mapper.RecordingEventMapper
import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingRequest
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface VideoRecordingSession {

    /** True while a recording from [start] has not been finalized yet. */
    val isRecording: StateFlow<Boolean>

    @RequiresPermission(
        value = Manifest.permission.RECORD_AUDIO,
        conditional = true,
    )
    fun start(
        request: RecordingRequest,
        onEvent: (RecordingEvent) -> Unit,
    ): Boolean

    fun setPaused(paused: Boolean)

    fun setMuted(muted: Boolean)

    fun stop()
}

internal class VideoRecordingSessionImpl @Inject constructor(
    private val cameraSession: CameraSession,
    private val recordingEventMapper: RecordingEventMapper,
    private val recordingPreparer: RecordingPreparer,
    @MainImmediateDispatcher mainDispatcher: CoroutineDispatcher,
) : VideoRecordingSession {

    private val mainExecutor: Executor = mainDispatcher.asExecutor()

    private var recording: Recording? = null
    private var unfinalizedRecordings = 0

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    @RequiresPermission(
        value = Manifest.permission.RECORD_AUDIO,
        conditional = true,
    )
    override fun start(
        request: RecordingRequest,
        onEvent: (RecordingEvent) -> Unit,
    ): Boolean {
        val recorder = cameraSession.videoCapture?.output ?: return false

        val outputOptions = FileDescriptorOutputOptions.Builder(request.fileDescriptor)
            .setLocation(request.location)
            .build()
        val pendingRecording = recordingPreparer.prepare(recorder, outputOptions)

        if (request.includeAudio) {
            pendingRecording.withAudioEnabled(initialMuted = true)
        }

        // Recorder reports an output it cannot open by finalizing from this very call, so the
        // events have to reach us posted rather than run inline: the first one would otherwise
        // arrive before start() has returned the recording it is about.
        recording = pendingRecording.start(mainExecutor) { event ->
            recordingEventMapper.map(event)?.let { recordingEvent ->
                if (recordingEvent is RecordingEvent.Finalized) {
                    onFinalized()
                }
                onEvent(recordingEvent)
            }
        }

        unfinalizedRecordings++
        _isRecording.value = true

        return true
    }

    override fun setPaused(paused: Boolean) {
        when {
            paused -> recording?.pause()
            else -> recording?.resume()
        }
    }

    override fun setMuted(muted: Boolean) {
        recording?.mute(muted)
    }

    override fun stop() {
        recording?.stop()
        recording?.close()
        recording = null
    }

    // The Recorder takes a new recording while the previous one is still being finalized.
    private fun onFinalized() {
        unfinalizedRecordings--
        _isRecording.value = unfinalizedRecordings > 0
    }
}
