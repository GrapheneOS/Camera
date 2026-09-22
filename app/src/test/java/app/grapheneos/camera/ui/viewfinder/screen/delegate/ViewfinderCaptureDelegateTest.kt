package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.net.Uri
import androidx.core.graphics.createBitmap
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.model.CapturePreviewResult
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.usecase.CaptureImage
import app.grapheneos.camera.domain.capture.usecase.CapturePreviewImage
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedPreview
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderChrome
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
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
    private val capturePreviewImage = mockk<CapturePreviewImage>()
    private val storeCapturedPreview = mockk<StoreCapturedPreview>()
    private val capturedItemRepository = mockk<CapturedItemRepository>()
    private val chrome = mockk<ViewfinderChrome>()

    private val onCaptureEvent = slot<(CapturedImageEvent) -> Unit>()
    private val captureFinished = CompletableDeferred<Unit>()

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.VIDEO, requiresVideoModeOnly = false),
        render = { ViewfinderUiState() },
    )

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

        delegate.showCapturedPreview()
        delegate.onScreenDestroyed()

        assertEquals(ViewfinderCaptureState(), capture())
    }

    @Test
    fun takePreviewPicture_handsTheBitmapBackWithoutSavingIt() {
        runTest {
            val bitmap = createBitmap(1, 1)
            coEvery { capturePreviewImage() } returns CapturePreviewResult.Captured(bitmap)

            val delegate = createDelegate(scope = backgroundScope)
            val events = collectEvents(delegate)
            delegate.takePreviewPicture()

            assertEquals(listOf(CapturedImageEvent.PreviewCaptured(bitmap)), events)
        }
    }

    @Test
    fun takePreviewPicture_withoutABoundCamera_saysNothing() {
        runTest {
            coEvery { capturePreviewImage() } returns CapturePreviewResult.Unavailable

            val delegate = createDelegate(scope = backgroundScope)
            val events = collectEvents(delegate)

            delegate.takePreviewPicture()

            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun confirmPreviewPicture_withAFileToWriteInto_storesTheBitmapThere() {
        runTest {
            val bitmap = createBitmap(1, 1)
            every { chrome.foreignOutputUri() } returns FOREIGN_URI
            coEvery { storeCapturedPreview(uri = FOREIGN_URI, bitmap = bitmap) } returns true

            val delegate = createDelegate(scope = backgroundScope)
            val events = collectEvents(delegate)

            delegate.confirmPreviewPicture(bitmap)

            assertEquals(listOf(CapturedImageEvent.PreviewStored), events)
        }
    }

    @Test
    fun confirmPreviewPicture_withoutAFile_handsTheBitmapBackInline() {
        runTest {
            every { chrome.foreignOutputUri() } returns null

            val delegate = createDelegate(scope = backgroundScope)
            val events = collectEvents(delegate)

            delegate.confirmPreviewPicture(createBitmap(1, 1))

            assertEquals(listOf(CapturedImageEvent.PreviewReturned), events)
            coVerify(exactly = 0) { storeCapturedPreview(uri = any(), bitmap = any()) }
        }
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
    fun takePicture_thatEndsWithoutAnyEvent_stillReleasesTheShutter() {
        runTest {
            val delegate = createDelegate(scope = backgroundScope)

            delegate.takePicture()
            assertTrue(capture().isTakingPicture)

            captureFinished.complete(Unit)

            assertFalse(capture().isTakingPicture)
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
        coEvery { captureImage(any(), any(), capture(onCaptureEvent)) } coAnswers {
            captureFinished.await()
        }
        every { capturedItemRepository.storageLocation } returns flowOf(STORAGE_LOCATION)
        every { chrome.thumbnailSize() } returns ThumbnailSize(width = 1, height = 1)

        val delegate = ViewfinderCaptureDelegateImpl(
            captureImage = captureImage,
            capturePreviewImage = capturePreviewImage,
            storeCapturedPreview = storeCapturedPreview,
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

        val FOREIGN_URI: Uri = Uri.parse("content://com.example.app/images/1")
    }
}
