package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderCaptureDelegateTest {

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.VIDEO, requiresVideoModeOnly = false),
        render = { ViewfinderUiState() },
    )

    @Test
    fun aPauseBeforeTheRecordingStarts_survivesTheStart() {
        val delegate = createDelegate()

        delegate.setRecordingPaused(paused = true)
        delegate.startRecording()

        assertEquals(
            ViewfinderCaptureState(
                recordingPhase = RecordingPhase.RECORDING,
                isRecordingPaused = true,
            ),
            capture(),
        )
    }

    @Test
    fun recording_goesFromRequestedToStartedToStopped() {
        val delegate = createDelegate()

        delegate.requestRecording()
        assertEquals(RecordingPhase.STARTING, capture().recordingPhase)

        delegate.startRecording()
        assertEquals(RecordingPhase.RECORDING, capture().recordingPhase)

        delegate.stopRecording()
        assertEquals(RecordingPhase.IDLE, capture().recordingPhase)
    }

    @Test
    fun stopRecording_beforeItStarted_returnsToIdle() {
        val delegate = createDelegate()

        delegate.requestRecording()
        delegate.stopRecording()

        assertEquals(RecordingPhase.IDLE, capture().recordingPhase)
    }

    @Test
    fun stopRecording_forgetsThePause() {
        val delegate = createDelegate()

        delegate.startRecording()
        delegate.setRecordingPaused(paused = true)
        delegate.stopRecording()

        assertEquals(ViewfinderCaptureState(), capture())
    }

    @Test
    fun pictureCapture_isInProgressUntilFinished() {
        val delegate = createDelegate()

        delegate.startPictureCapture()
        assertTrue(capture().isTakingPicture)

        delegate.finishPictureCapture()
        assertFalse(capture().isTakingPicture)
    }

    @Test
    fun pictureSave_isInProgressUntilFinished() {
        val delegate = createDelegate()

        delegate.startPictureSave()
        assertTrue(capture().isSavingPicture)

        delegate.finishPictureSave()
        assertFalse(capture().isSavingPicture)
    }

    @Test
    fun selfTimer_countsDownOneSecondApart() = runTest {
        val ticks = mutableListOf<Pair<Int, Long>>()

        createDelegate().selfTimerCountdown(seconds = 3).collect { ticks += it to currentTime }

        assertEquals(listOf(3 to 0L, 2 to 1_000L, 1 to 2_000L), ticks)
    }

    @Test
    fun selfTimer_endsASecondAfterTheLastTick() = runTest {
        createDelegate().selfTimerCountdown(seconds = 3).collect {}

        assertEquals(3_000L, currentTime)
    }

    @Test
    fun selfTimer_isRunningUntilStopped() {
        val delegate = createDelegate()

        delegate.setSelfTimerRunning(true)
        assertTrue(capture().isSelfTimerRunning)

        delegate.setSelfTimerRunning(false)
        assertFalse(capture().isSelfTimerRunning)
    }

    @Test
    fun capturedPreview_isShownUntilDismissed() {
        val delegate = createDelegate()

        delegate.showCapturedPreview()
        assertTrue(capture().isCapturedPreviewShown)

        delegate.dismissCapturedPreview()
        assertFalse(capture().isCapturedPreviewShown)
    }

    @Test
    fun onScreenDestroyed_forgetsWhatTheScreenWasShowing() {
        val delegate = createDelegate()

        delegate.startRecording()
        delegate.showCapturedPreview()
        delegate.onScreenDestroyed()

        assertEquals(ViewfinderCaptureState(), capture())
    }

    private fun capture(): ViewfinderCaptureState {
        return stateHolder.state.value.capture
    }

    private fun createDelegate(): ViewfinderCaptureDelegate {
        val delegate = ViewfinderCaptureDelegateImpl()

        delegate.bind(stateHolder)

        return delegate
    }
}
