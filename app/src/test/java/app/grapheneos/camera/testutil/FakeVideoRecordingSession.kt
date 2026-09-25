package app.grapheneos.camera.testutil

import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingRequest
import app.grapheneos.camera.data.camera.session.VideoRecordingSession

internal class FakeVideoRecordingSession : VideoRecordingSession {

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
        requireNotNull(onEvent) { "no recording was started" }.invoke(event)
    }
}
