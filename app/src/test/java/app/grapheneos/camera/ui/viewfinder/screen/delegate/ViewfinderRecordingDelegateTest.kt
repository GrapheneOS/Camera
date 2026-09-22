package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ViewfinderRecordingDelegateTest {

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.VIDEO, requiresVideoModeOnly = false),
        render = { ViewfinderUiState() },
    )

    @Test
    fun aPauseBeforeTheRecordingStarts_survivesTheStart() {
        val delegate = createDelegate()

        delegate.setPaused(paused = true)
        delegate.startRecording()

        assertEquals(
            ViewfinderRecordingState(
                phase = RecordingPhase.RECORDING,
                isPaused = true,
            ),
            recording(),
        )
    }

    @Test
    fun recording_goesFromRequestedToStartedToStopped() {
        val delegate = createDelegate()

        delegate.requestRecording()
        assertEquals(RecordingPhase.STARTING, recording().phase)

        delegate.startRecording()
        assertEquals(RecordingPhase.RECORDING, recording().phase)

        delegate.stopRecording()
        assertEquals(RecordingPhase.IDLE, recording().phase)
    }

    @Test
    fun stopRecording_beforeItStarted_returnsToIdle() {
        val delegate = createDelegate()

        delegate.requestRecording()
        delegate.stopRecording()

        assertEquals(RecordingPhase.IDLE, recording().phase)
    }

    @Test
    fun requestRecording_doesNotCarryOverThePreviousPauseOrMute() {
        val delegate = createDelegate()

        delegate.setPaused(paused = true)
        delegate.setMuted(muted = true)
        delegate.requestRecording()

        assertFalse(recording().isPaused)
        assertFalse(recording().isMuted)
    }

    @Test
    fun aNewRecording_startsFromZeroAfterTheLastOne() {
        val delegate = createDelegate()

        delegate.requestRecording()
        delegate.startRecording()
        delegate.setRecordedDuration(42.seconds)
        delegate.stopRecording()
        delegate.requestRecording()

        assertEquals(Duration.ZERO, recording().duration)
    }

    @Test
    fun stopRecording_forgetsEverythingTheRecordingHeld() {
        val delegate = createDelegate()

        delegate.startRecording()
        delegate.setPaused(paused = true)
        delegate.setMuted(muted = true)
        delegate.setRecordedDuration(42.seconds)
        delegate.stopRecording()

        assertEquals(ViewfinderRecordingState(), recording())
    }

    private fun recording(): ViewfinderRecordingState {
        return stateHolder.state.value.recording
    }

    private fun createDelegate(): ViewfinderRecordingDelegate {
        val delegate = ViewfinderRecordingDelegateImpl()

        delegate.bind(stateHolder)

        return delegate
    }
}
