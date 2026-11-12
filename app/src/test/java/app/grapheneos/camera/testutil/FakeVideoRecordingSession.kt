package app.grapheneos.camera.testutil

import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingRequest
import app.grapheneos.camera.data.camera.session.VideoRecordingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FakeVideoRecordingSession : VideoRecordingSession {

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    var startCount = 0
        private set

    var stopCount = 0
        private set

    var isPaused = false
        private set

    var isMuted = false
        private set

    private var onEvent: ((RecordingEvent) -> Unit)? = null

    override fun start(
        request: RecordingRequest,
        onEvent: (RecordingEvent) -> Unit,
    ): Boolean {
        startCount++
        this.onEvent = onEvent
        _isRecording.value = true
        return true
    }

    override fun setPaused(paused: Boolean) {
        if (onEvent != null) {
            isPaused = paused
        }
    }

    override fun setMuted(muted: Boolean) {
        if (onEvent != null) {
            isMuted = muted
        }
    }

    override fun stop() {
        stopCount++
    }

    fun emit(event: RecordingEvent) {
        if (event is RecordingEvent.Finalized) {
            _isRecording.value = false
        }
        requireNotNull(onEvent) { "no recording was started" }.invoke(event)
    }
}
