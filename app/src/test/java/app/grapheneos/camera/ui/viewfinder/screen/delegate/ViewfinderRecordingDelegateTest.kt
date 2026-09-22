package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.net.Uri
import app.grapheneos.camera.data.camera.session.VideoRecordingSession
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.domain.capture.model.RecordingOutput
import app.grapheneos.camera.domain.capture.usecase.CreateRecordingOutput
import app.grapheneos.camera.domain.capture.usecase.DiscardRecording
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderChrome
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingUpdate
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderRecordingDelegateTest {

    private val recordingSession = mockk<VideoRecordingSession>(relaxed = true)
    private val createRecordingOutput = mockk<CreateRecordingOutput>()
    private val discardRecording = mockk<DiscardRecording>(relaxed = true)
    private val chrome = mockk<ViewfinderChrome>(relaxed = true)

    private val output = RecordingOutput(
        uri = Uri.EMPTY,
        dateString = "20260922_120000",
        fileDescriptor = mockk(relaxed = true),
        isOwnFile = true,
        isPendingMediaStoreUri = false,
    )

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

        delegate.markStopped()
        assertEquals(RecordingPhase.IDLE, recording().phase)
    }

    @Test
    fun stopRecording_beforeItStarted_returnsToIdle() {
        val delegate = createDelegate()

        delegate.requestRecording()
        delegate.markStopped()

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
        delegate.markStopped()
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
        delegate.markStopped()

        assertEquals(ViewfinderRecordingState(), recording())
    }

    @Test
    fun startPreparedRecording_startsOnlyTheFirstTimeTheSoundReportsBack() {
        runTest {
            coEvery { createRecordingOutput(any(), any()) } returns output

            val delegate = createDelegate(scope = backgroundScope)
            delegate.requestRecording()
            delegate.prepareRecording(includeLocation = false, includeAudio = false)

            delegate.startPreparedRecording()
            delegate.startPreparedRecording()

            verify(exactly = 1) { recordingSession.start(any(), any()) }
        }
    }

    @Test
    fun requestStop_beforeTheStartSoundEnded_throwsTheOutputAway() {
        runTest {
            coEvery { createRecordingOutput(any(), any()) } returns output

            val delegate = createDelegate(scope = backgroundScope)
            val updates = collectUpdates(delegate)
            delegate.requestRecording()
            delegate.prepareRecording(includeLocation = false, includeAudio = false)

            delegate.requestStop()

            coVerify(exactly = 1) { discardRecording.invoke(output) }
            verify(exactly = 0) { recordingSession.stop() }
            assertTrue(RecordingUpdate.Abandoned in updates)
        }
    }

    private fun TestScope.collectUpdates(
        delegate: ViewfinderRecordingDelegate,
    ): List<RecordingUpdate> {
        val updates = mutableListOf<RecordingUpdate>()

        backgroundScope.launch { delegate.recordingUpdates.collect { updates += it } }

        return updates
    }

    private fun recording(): ViewfinderRecordingState {
        return stateHolder.state.value.recording
    }

    private fun createDelegate(
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    ): ViewfinderRecordingDelegate {
        val delegate = ViewfinderRecordingDelegateImpl(
            recordingSession = recordingSession,
            createRecordingOutput = createRecordingOutput,
            publishRecording = mockk(relaxed = true),
            discardRecording = discardRecording,
            capturedItemRepository = mockk {
                every { storageLocation } returns flowOf(STORAGE_LOCATION)
            },
            locationRepository = mockk(relaxed = true),
            applicationScope = scope,
            mainDispatcher = UnconfinedTestDispatcher(),
        )

        delegate.bind(stateHolder)

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
