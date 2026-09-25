package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.net.Uri
import androidx.core.graphics.createBitmap
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.domain.capture.model.CapturePreviewResult
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.model.ImageSaverException
import app.grapheneos.camera.domain.capture.usecase.CaptureImage
import app.grapheneos.camera.domain.capture.usecase.CapturePreviewImage
import app.grapheneos.camera.domain.capture.usecase.NotifyPictureSaveFailed
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedPreview
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
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
    private val notifyPictureSaveFailed = mockk<NotifyPictureSaveFailed>(relaxed = true)

    private val onCaptureEvent = slot<(CapturedImageEvent) -> Unit>()
    private val captureFinished = CompletableDeferred<Unit>()

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.VIDEO)

    @Test
    fun pictureSave_isInProgressUntilFinished() {
        runTest {
            val delegate = createDelegate()

            delegate.startPictureSave()
            assertTrue(capture().isSavingPicture)

            delegate.finishPictureSave()
            assertFalse(capture().isSavingPicture)
        }
    }

    @Test
    fun recordingSave_isInProgressUntilFinished() {
        runTest {
            val delegate = createDelegate()

            delegate.startRecordingSave()
            assertTrue(capture().isSavingRecording)

            delegate.finishRecordingSave()
            assertFalse(capture().isSavingRecording)
        }
    }

    @Test
    fun capturedPreview_isShownUntilDismissed() {
        runTest {
            val delegate = createDelegate()

            delegate.showCapturedPreview()
            assertTrue(capture().isCapturedPreviewShown)

            delegate.dismissCapturedPreview()
            assertFalse(capture().isCapturedPreviewShown)
        }
    }

    @Test
    fun onScreenDestroyed_forgetsWhatTheScreenWasShowing() {
        runTest {
            val delegate = createDelegate()

            delegate.showCapturedPreview()
            delegate.onScreenDestroyed()

            assertEquals(ViewfinderCaptureState(), capture())
        }
    }

    @Test
    fun takePreviewPicture_handsTheBitmapBackWithoutSavingIt() {
        runTest {
            val bitmap = createBitmap(1, 1)
            coEvery { capturePreviewImage() } returns CapturePreviewResult.Captured(bitmap)

            val delegate = createDelegate()
            val events = collectEvents(delegate)

            delegate.takePreviewPicture()

            assertEquals(listOf(CapturedImageEvent.PreviewCaptured(bitmap)), events)
        }
    }

    @Test
    fun takePreviewPicture_withoutABoundCamera_saysNothing() {
        runTest {
            coEvery { capturePreviewImage() } returns CapturePreviewResult.Unavailable

            val delegate = createDelegate()
            val events = collectEvents(delegate)

            delegate.takePreviewPicture()

            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun confirmPreviewPicture_withAFileToWriteInto_storesTheBitmapThere() {
        runTest {
            val bitmap = createBitmap(1, 1)
            coEvery { storeCapturedPreview(uri = FOREIGN_URI, bitmap = bitmap) } returns true

            val delegate = createDelegate()
            val events = collectEvents(delegate)

            delegate.confirmPreviewPicture(bitmap = bitmap, outputUri = FOREIGN_URI)

            assertEquals(listOf(CapturedImageEvent.PreviewStored), events)
        }
    }

    @Test
    fun confirmPreviewPicture_withoutAFile_handsTheBitmapBackInline() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)

            delegate.confirmPreviewPicture(bitmap = createBitmap(1, 1), outputUri = null)

            assertEquals(listOf(CapturedImageEvent.PreviewReturned), events)
            coVerify(exactly = 0) { storeCapturedPreview(uri = any(), bitmap = any()) }
        }
    }

    @Test
    fun takePicture_marksTheCaptureAndReportsWhatTheUseCaseEmits() {
        runTest {
            val delegate = createDelegate()
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
            val delegate = createDelegate()

            delegate.takePicture()
            assertTrue(capture().isTakingPicture)

            captureFinished.complete(Unit)

            assertFalse(capture().isTakingPicture)
        }
    }

    @Test
    fun cancelPictureCapture_keepsTheFailureItCausesQuiet() {
        runTest {
            val delegate = createDelegate()
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

    @Test
    fun cancelPictureCapture_beforeTheRequestIsMade_takesNoPicture() {
        runTest {
            val storageLocationRead = CompletableDeferred<Unit>()
            val delegate = createDelegate()
            every { capturedItemRepository.storageLocation } returns flow {
                storageLocationRead.await()
                emit(STORAGE_LOCATION)
            }

            delegate.takePicture()
            delegate.cancelPictureCapture()
            storageLocationRead.complete(Unit)

            coVerify(exactly = 0) { captureImage(any(), any(), any()) }
            assertFalse(capture().isTakingPicture)
        }
    }

    @Test
    fun aSavedPicture_isStoredAsTheLastCaptureWithNobodyListening() {
        runTest {
            val delegate = createDelegate()

            delegate.takePicture()
            emitCaptureEvent(CapturedImageEvent.Saved(item = SAVED_ITEM))

            coVerify(exactly = 1) { capturedItemRepository.saveLastCapturedItem(SAVED_ITEM) }
        }
    }

    @Test
    fun aSaveFailure_onScreen_isReportedWithoutANotification() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)
            delegate.onScreenStarted()

            delegate.takePicture()
            emitCaptureEvent(SAVE_FAILED)

            assertEquals(listOf(SAVE_FAILED), events)
            coVerify(exactly = 0) { notifyPictureSaveFailed() }
        }
    }

    @Test
    fun aSaveFailure_offScreen_isNotifiedInsteadOfReported() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)
            delegate.onScreenStarted()
            delegate.onScreenStopped()

            delegate.takePicture()
            delegate.startPictureSave()
            emitCaptureEvent(SAVE_FAILED)

            assertTrue(events.isEmpty())
            assertFalse(capture().isSavingPicture)
            coVerify(exactly = 1) { notifyPictureSaveFailed() }
        }
    }

    @Test
    fun aCaptureFailure_offScreen_isNotReported() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)

            delegate.takePicture()
            emitCaptureEvent(
                CapturedImageEvent.CaptureFailed(errorCode = 1, cause = IOException("failed")),
            )

            assertFalse(capture().isTakingPicture)
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun selfTimer_countsDownOneSecondApartAndThenFinishes() {
        runTest {
            val delegate = createDelegate()
            val timeline = mutableListOf<Pair<Effect, Long>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                stateHolder.effects.collect { effect -> timeline += effect to currentTime }
            }
            stateHolder.update {
                it.copy(settings = CameraSettings(selfTimerDurationSeconds = SELF_TIMER_SECONDS))
            }

            delegate.startSelfTimer()

            advanceTimeBy(SELF_TIMER_SECONDS.seconds)
            runCurrent()

            assertEquals(
                listOf(
                    Effect.SelfTimer.Started to 0L,
                    Effect.SelfTimer.Ticked(secondsLeft = 3) to 0L,
                    Effect.SelfTimer.Ticked(secondsLeft = 2) to 1_000L,
                    Effect.SelfTimer.Ticked(secondsLeft = 1) to 2_000L,
                    Effect.SelfTimer.Finished to 3_000L,
                ),
                timeline,
            )
            assertFalse(capture().isSelfTimerRunning)
        }
    }

    @Test
    fun selfTimer_isRunningUntilCancelled() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            stateHolder.update {
                it.copy(settings = CameraSettings(selfTimerDurationSeconds = SELF_TIMER_SECONDS))
            }

            delegate.startSelfTimer()
            assertTrue(capture().isSelfTimerRunning)

            delegate.cancelSelfTimer()
            assertFalse(capture().isSelfTimerRunning)
            assertEquals(Effect.SelfTimer.Cancelled, effects.last())
        }
    }

    @Test
    fun selfTimerCancelled_withoutACountdown_putsNothingBack() {
        runTest {
            val effects = collectEffects(stateHolder)

            createDelegate().cancelSelfTimer()

            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun screenDestroyed_dropsTheCountdownWithoutPuttingTheControlsBack() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            stateHolder.update {
                it.copy(settings = CameraSettings(selfTimerDurationSeconds = SELF_TIMER_SECONDS))
            }

            delegate.startSelfTimer()
            delegate.onScreenDestroyed()
            delegate.cancelSelfTimer()

            advanceTimeBy(SELF_TIMER_SECONDS.seconds)
            runCurrent()

            assertEquals(
                listOf(Effect.SelfTimer.Started, Effect.SelfTimer.Ticked(secondsLeft = 3)),
                effects,
            )
        }
    }

    private fun emitCaptureEvent(event: CapturedImageEvent) {
        onCaptureEvent.captured(event)
    }

    private fun TestScope.collectEvents(
        delegate: ViewfinderCaptureDelegate,
    ): List<CapturedImageEvent> {
        val events = mutableListOf<CapturedImageEvent>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            delegate.captureEvents.collect { events += it }
        }

        return events
    }

    private fun capture(): ViewfinderCaptureState {
        return stateHolder.state.value.capture
    }

    private fun TestScope.createDelegate(): ViewfinderCaptureDelegate {
        coEvery { captureImage(any(), any(), capture(onCaptureEvent)) } coAnswers {
            captureFinished.await()
        }
        every { capturedItemRepository.storageLocation } returns flowOf(STORAGE_LOCATION)
        coEvery { capturedItemRepository.saveLastCapturedItem(any()) } just runs

        val delegate = ViewfinderCaptureDelegateImpl(
            captureImage = captureImage,
            capturePreviewImage = capturePreviewImage,
            storeCapturedPreview = storeCapturedPreview,
            capturedItemRepository = capturedItemRepository,
            notifyPictureSaveFailed = notifyPictureSaveFailed,
            applicationScope = backgroundScope,
            mainDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        delegate.bind(
            scope = backgroundScope,
            stateHolder = stateHolder,
        )
        delegate.onScreenCreated(
            ViewfinderHost(
                previewTarget = mockk(relaxed = true),
                previewFrames = mockk(relaxed = true),
                thumbnailSize = ThumbnailSize(width = 1, height = 1),
            ),
        )

        return delegate
    }

    private companion object {
        const val SELF_TIMER_SECONDS = 3
        const val STORAGE_LOCATION = "MediaStore"

        val FOREIGN_URI: Uri = Uri.parse("content://com.example.app/images/1")

        val SAVED_ITEM = CapturedItem(
            type = ITEM_TYPE_IMAGE,
            dateString = "20260926_120000",
            uri = Uri.parse("content://media/external/images/media/1"),
        )

        val SAVE_FAILED = CapturedImageEvent.SaveFailed(
            cause = ImageSaverException(ImageSaverException.Place.FILE_WRITE),
            alreadyReported = false,
        )
    }
}
