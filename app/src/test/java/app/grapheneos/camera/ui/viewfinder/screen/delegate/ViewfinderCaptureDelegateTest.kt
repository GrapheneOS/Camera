package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.net.Uri
import androidx.core.graphics.createBitmap
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.R
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
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureEvent
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
    fun takePreviewPicture_showsTheBitmapWithoutSavingIt() {
        runTest {
            val bitmap = createBitmap(1, 1)
            coEvery { capturePreviewImage() } returns CapturePreviewResult.Captured(bitmap)
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)

            delegate.takePreviewPicture()

            assertFalse(capture().isSavingPicture)
            assertEquals(
                listOf(
                    Effect.Picture.PreviewCaptured(bitmap),
                    Effect.ShowMessage(R.string.image_captured_successfully),
                ),
                effects,
            )
        }
    }

    @Test
    fun takePreviewPicture_thatFails_reportsIt() {
        runTest {
            coEvery { capturePreviewImage() } returns CapturePreviewResult.Failed
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)

            delegate.takePreviewPicture()

            assertFalse(capture().isSavingPicture)
            assertEquals(listOf(Effect.Picture.PreviewFailed), effects)
        }
    }

    @Test
    fun takePreviewPicture_withoutABoundCamera_saysNothing() {
        runTest {
            coEvery { capturePreviewImage() } returns CapturePreviewResult.Unavailable
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)

            delegate.takePreviewPicture()

            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun confirmPreviewPicture_withAFileToWriteInto_storesTheBitmapThere() {
        runTest {
            val bitmap = createBitmap(1, 1)
            coEvery { storeCapturedPreview(uri = FOREIGN_URI, bitmap = bitmap) } returns true
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)

            delegate.confirmPreviewPicture(bitmap = bitmap, outputUri = FOREIGN_URI)

            assertEquals(listOf(Effect.Picture.PreviewStored), effects)
        }
    }

    @Test
    fun confirmPreviewPicture_thatCannotBeStored_saysSo() {
        runTest {
            val bitmap = createBitmap(1, 1)
            coEvery { storeCapturedPreview(uri = FOREIGN_URI, bitmap = bitmap) } returns false
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)

            delegate.confirmPreviewPicture(bitmap = bitmap, outputUri = FOREIGN_URI)

            assertEquals(listOf(Effect.Picture.PreviewStoreFailed), effects)
        }
    }

    @Test
    fun confirmPreviewPicture_withoutAFile_handsTheBitmapBackInline() {
        runTest {
            val bitmap = createBitmap(1, 1)
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)

            delegate.confirmPreviewPicture(bitmap = bitmap, outputUri = null)

            assertEquals(listOf(Effect.Picture.PreviewReturned(bitmap = bitmap)), effects)
            coVerify(exactly = 0) { storeCapturedPreview(uri = any(), bitmap = any()) }
        }
    }

    @Test
    fun captured_releasesTheShutterAndSoundsItBeforeFlashingThePreview() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)

            delegate.takePicture()
            assertTrue(capture().isTakingPicture)

            emitCaptureEvent(CapturedImageEvent.Captured)

            assertFalse(capture().isTakingPicture)
            assertTrue(capture().isSavingPicture)
            assertEquals(
                listOf(
                    Effect.Picture.Captured,
                    Effect.FlashPreview(selfIlluminate = false),
                ),
                effects,
            )
        }
    }

    @Test
    fun thumbnailReady_finishesTheSaveAndHandsTheThumbnailOn() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)
            val thumbnail = createBitmap(1, 1)

            delegate.takePicture()
            emitCaptureEvent(CapturedImageEvent.Captured)
            emitCaptureEvent(CapturedImageEvent.ThumbnailReady(thumbnail = thumbnail))

            assertFalse(capture().isSavingPicture)
            assertEquals(listOf(ViewfinderCaptureEvent.ThumbnailReady(thumbnail)), events)
        }
    }

    @Test
    fun savedAndStorageLocationNotFound_areHandedOn() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)

            delegate.takePicture()
            emitCaptureEvent(CapturedImageEvent.StorageLocationNotFound)
            emitCaptureEvent(CapturedImageEvent.Saved(item = ITEM))

            assertEquals(
                listOf(
                    ViewfinderCaptureEvent.StorageLocationNotFound,
                    ViewfinderCaptureEvent.Saved(ITEM),
                ),
                events,
            )
        }
    }

    @Test
    fun aSavedPicture_isStoredAsTheLastCaptureWithNobodyListening() {
        runTest {
            val delegate = createDelegate()

            delegate.takePicture()
            emitCaptureEvent(CapturedImageEvent.Saved(item = ITEM))

            coVerify(exactly = 1) { capturedItemRepository.saveLastCapturedItem(ITEM) }
        }
    }

    @Test
    fun locationUnavailable_saysSo() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)

            delegate.takePicture()
            emitCaptureEvent(CapturedImageEvent.LocationUnavailable)

            assertEquals(listOf(Effect.ShowMessage(R.string.location_unavailable)), effects)
        }
    }

    @Test
    fun captureFailed_onScreen_reportsTheFailure() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            val cause = IOException("no camera")
            delegate.onScreenStarted()

            delegate.takePicture()
            emitCaptureEvent(
                CapturedImageEvent.CaptureFailed(errorCode = CAPTURE_ERROR_CODE, cause = cause),
            )

            assertFalse(capture().isSavingPicture)
            val failure = effects.single() as Effect.Picture.CaptureFailed
            assertEquals(CAPTURE_ERROR_CODE, failure.errorCode)
            assertEquals(cause.javaClass.name, failure.details.name)
        }
    }

    @Test
    fun captureFailed_offScreen_isNotReported() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            delegate.onScreenStarted()
            delegate.onScreenStopped()

            delegate.takePicture()
            emitCaptureEvent(
                CapturedImageEvent.CaptureFailed(
                    errorCode = CAPTURE_ERROR_CODE,
                    cause = IOException("no camera"),
                ),
            )

            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun saveFailed_onScreen_reportsTheStageThatFailed() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            delegate.onScreenStarted()

            delegate.takePicture()
            emitCaptureEvent(CapturedImageEvent.Captured)
            emitCaptureEvent(saveFailure(alreadyReported = true))

            assertFalse(capture().isSavingPicture)
            val failure = effects.last() as Effect.Picture.SaveFailed
            assertEquals(SAVE_FAILURE_STAGE, failure.stage)
            assertTrue(failure.alreadyReported)
            coVerify(exactly = 0) { notifyPictureSaveFailed() }
        }
    }

    @Test
    fun saveFailed_offScreen_postsANotificationInstead() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)

            delegate.takePicture()
            emitCaptureEvent(saveFailure(alreadyReported = false))

            coVerify(exactly = 1) { notifyPictureSaveFailed() }
            assertTrue(effects.isEmpty())
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
            val effects = collectEffects(stateHolder)
            delegate.onScreenStarted()

            delegate.takePicture()
            delegate.cancelPictureCapture()
            emitCaptureEvent(
                CapturedImageEvent.CaptureFailed(errorCode = 1, cause = IOException("cancelled")),
            )

            assertFalse(capture().isTakingPicture)
            assertTrue(effects.isEmpty())
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

    private fun saveFailure(alreadyReported: Boolean): CapturedImageEvent {
        return CapturedImageEvent.SaveFailed(
            cause = ImageSaverException(ImageSaverException.Place.FILE_WRITE),
            alreadyReported = alreadyReported,
        )
    }

    private fun TestScope.collectEvents(
        delegate: ViewfinderCaptureDelegate,
    ): List<ViewfinderCaptureEvent> {
        val events = mutableListOf<ViewfinderCaptureEvent>()

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
        const val CAPTURE_ERROR_CODE = 2
        const val SAVE_FAILURE_STAGE = "FILE_WRITE"

        val FOREIGN_URI: Uri = Uri.parse("content://com.example.app/images/1")
        val ITEM = CapturedItem(ITEM_TYPE_IMAGE, "20260920_120000_000", Uri.EMPTY)
    }
}
