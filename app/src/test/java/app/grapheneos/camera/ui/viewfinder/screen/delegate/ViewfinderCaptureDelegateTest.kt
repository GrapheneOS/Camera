package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.usecase.CaptureImage
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderChrome
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private val captureImage = mockk<CaptureImage>()
    private val capturedItemRepository = mockk<CapturedItemRepository>()
    private val chrome = mockk<ViewfinderChrome>()

    private val onCaptureEvent = slot<(CapturedImageEvent) -> Unit>()

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
    fun requestRecording_doesNotCarryOverThePreviousPauseOrMute() {
        val delegate = createDelegate()

        delegate.setRecordingPaused(paused = true)
        delegate.setRecordingMuted(muted = true)
        delegate.requestRecording()

        assertFalse(capture().isRecordingPaused)
        assertFalse(capture().isRecordingMuted)
    }

    @Test
    fun stopRecording_forgetsTheMute() {
        val delegate = createDelegate()

        delegate.startRecording()
        delegate.setRecordingMuted(muted = true)
        delegate.stopRecording()

        assertFalse(capture().isRecordingMuted)
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
    fun pictureSave_isInProgressUntilFinished() {
        val delegate = createDelegate()

        delegate.startPictureSave()
        assertTrue(capture().isSavingPicture)

        delegate.finishPictureSave()
        assertFalse(capture().isSavingPicture)
    }

    @Test
    fun selfTimer_countsDownOneSecondApart() {
        runTest {
            val ticks = mutableListOf<Pair<Int, Long>>()

            createDelegate().selfTimerCountdown(seconds = 3).collect { ticks += it to currentTime }

            assertEquals(listOf(3 to 0L, 2 to 1_000L, 1 to 2_000L), ticks)
        }
    }

    @Test
    fun selfTimer_endsASecondAfterTheLastTick() {
        runTest {
            createDelegate().selfTimerCountdown(seconds = 3).collect {}

            assertEquals(3_000L, currentTime)
        }
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

    @Test
    fun takePicture_marksTheCaptureAndReportsWhatTheUseCaseEmits() {
        runTest {
            val delegate = createDelegate(scope = backgroundScope)
            val events = collectEvents(delegate)

            delegate.takePicture()
            assertTrue(capture().isTakingPicture)

            emitCaptureEvent(CapturedImageEvent.Captured)

            assertFalse(capture().isTakingPicture)
            assertEquals(listOf(CapturedImageEvent.Captured), events)
        }
    }

    @Test
    fun cancelPictureCapture_keepsTheFailureItCausesQuiet() {
        runTest {
            val delegate = createDelegate(scope = backgroundScope)
            val events = collectEvents(delegate)

            delegate.takePicture()
            delegate.cancelPictureCapture()
            emitCaptureEvent(
                CapturedImageEvent.CaptureFailed(errorCode = 1, cause = IOException("cancelled")),
            )

            assertFalse(capture().isTakingPicture)
            assertTrue(events.isEmpty())
        }
    }

    private fun emitCaptureEvent(event: CapturedImageEvent) {
        onCaptureEvent.captured(event)
    }

    private fun TestScope.collectEvents(
        delegate: ViewfinderCaptureDelegate,
    ): List<CapturedImageEvent> {
        val events = mutableListOf<CapturedImageEvent>()

        backgroundScope.launch { delegate.captureEvents.collect { events += it } }

        return events
    }

    private fun capture(): ViewfinderCaptureState {
        return stateHolder.state.value.capture
    }

    private fun createDelegate(
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    ): ViewfinderCaptureDelegate {
        coEvery { captureImage(any(), any(), capture(onCaptureEvent)) } returns Unit
        every { capturedItemRepository.storageLocation } returns flowOf(STORAGE_LOCATION)
        every { chrome.thumbnailSize() } returns ThumbnailSize(width = 1, height = 1)

        val delegate = ViewfinderCaptureDelegateImpl(
            captureImage = captureImage,
            capturedItemRepository = capturedItemRepository,
            mainDispatcher = UnconfinedTestDispatcher(),
        )

        delegate.bind(scope = scope, stateHolder = stateHolder)
        delegate.onScreenCreated(
            ViewfinderHost(
                previewTarget = mockk(relaxed = true),
                chrome = chrome,
                previewFrames = mockk(relaxed = true),
            ),
        )

        return delegate
    }

    private companion object {
        const val STORAGE_LOCATION = "MediaStore"
    }
}
