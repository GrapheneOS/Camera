package app.grapheneos.camera.data.camera.session

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.Recording
import app.grapheneos.camera.data.camera.mapper.RecordingEventMapper
import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface VideoRecordingSession {

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
    @ApplicationContext private val context: Context,
) : VideoRecordingSession {

    private var recording: Recording? = null

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
        val pendingRecording = recorder.prepareRecording(context, outputOptions)

        if (request.includeAudio) {
            pendingRecording.withAudioEnabled()
        }

        // Recorder reports an output it cannot open by finalizing from this very call, so the
        // events have to reach us posted rather than run inline: the first one would otherwise
        // arrive before start() has returned the recording it is about.
        recording = pendingRecording.start(context.mainExecutor) { event ->
            recordingEventMapper.map(event)?.let(onEvent)
        }

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
}
