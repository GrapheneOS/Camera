package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.net.Uri
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.domain.capture.coordinator.VideoRecorder
import app.grapheneos.camera.domain.capture.model.RecordVideoRequest
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderChrome
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

    private val videoRecorder = mockk<VideoRecorder>(relaxed = true)
    private val chrome = mockk<ViewfinderChrome>(relaxed = true)

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.VIDEO)

    @Test
    fun aPauseBeforeTheRecordingStarts_survivesTheStart() {
        runTest {
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
    }

    @Test
    fun recording_goesFromRequestedToStartedToStopped() {
        runTest {
            val delegate = createDelegate()

            delegate.requestRecording()
            assertEquals(RecordingPhase.STARTING, recording().phase)

            delegate.startRecording()
            assertEquals(RecordingPhase.RECORDING, recording().phase)

            delegate.markStopped()
            assertEquals(RecordingPhase.IDLE, recording().phase)
        }
    }

    @Test
    fun markStopped_beforeTheRecordingStarted_returnsToIdle() {
        runTest {
            val delegate = createDelegate()

            delegate.requestRecording()
            delegate.markStopped()

            assertEquals(RecordingPhase.IDLE, recording().phase)
        }
    }

    @Test
    fun requestRecording_doesNotCarryOverThePreviousPauseOrMute() {
        runTest {
            val delegate = createDelegate()

            delegate.setPaused(paused = true)
            delegate.setMuted(muted = true)
            delegate.requestRecording()

            assertFalse(recording().isPaused)
            assertFalse(recording().isMuted)
        }
    }

    @Test
    fun markStopped_forgetsEverythingTheRecordingHeld() {
        runTest {
            val delegate = createDelegate()

            delegate.startRecording()
            delegate.setPaused(paused = true)
            delegate.setMuted(muted = true)
            delegate.setRecordedDuration(42.seconds)
            delegate.markStopped()

            assertEquals(ViewfinderRecordingState(), recording())
        }
    }

    @Test
    fun prepareRecording_handsTheRecorderWhereToWriteAndWhetherItIsStillWanted() {
        runTest {
            every { chrome.foreignOutputUri() } returns FOREIGN_URI

            val isStillWanted = slot<() -> Boolean>()
            val delegate = createDelegate()
            delegate.requestRecording()
            delegate.prepareRecording(includeLocation = true, includeAudio = false)

            coVerify(exactly = 1) {
                videoRecorder.prepare(
                    request = RecordVideoRequest(
                        storageLocation = STORAGE_LOCATION,
                        foreignUri = FOREIGN_URI,
                        includeLocation = true,
                        includeAudio = false,
                    ),
                    isStillWanted = capture(isStillWanted),
                )
            }
            assertTrue(isStillWanted.captured())

            delegate.markStopped()
            assertFalse(isStillWanted.captured())
        }
    }

    @Test
    fun startPreparedRecording_passesOnThePauseAndMuteSetBeforeTheStart() {
        runTest {
            val delegate = createDelegate()

            delegate.requestRecording()
            delegate.setPaused(paused = true)
            delegate.setMuted(muted = true)
            delegate.startPreparedRecording()

            verify(exactly = 1) { videoRecorder.start(muted = true, paused = true) }
        }
    }

    private fun recording(): ViewfinderRecordingState {
        return stateHolder.state.value.recording
    }

    private fun TestScope.createDelegate(): ViewfinderRecordingDelegate {
        val delegate = ViewfinderRecordingDelegateImpl(
            videoRecorder = videoRecorder,
            capturedItemRepository = mockk {
                every { storageLocation } returns flowOf(STORAGE_LOCATION)
            },
            applicationScope = backgroundScope,
            mainDispatcher = UnconfinedTestDispatcher(testScheduler),
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

        val FOREIGN_URI: Uri = Uri.parse("content://com.example.app/videos/1")
    }
}
