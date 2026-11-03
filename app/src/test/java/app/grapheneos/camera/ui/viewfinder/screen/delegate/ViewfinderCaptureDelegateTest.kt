package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.net.Uri
import android.os.Build
import androidx.core.graphics.createBitmap
import app.grapheneos.camera.BuildConfig
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.sound.model.CameraSound
import app.grapheneos.camera.domain.camera.usecase.BlinkTimerLight
import app.grapheneos.camera.domain.capture.model.CapturePreviewResult
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.model.ImageSaverException
import app.grapheneos.camera.domain.capture.usecase.CaptureImage
import app.grapheneos.camera.domain.capture.usecase.CapturePreviewImage
import app.grapheneos.camera.domain.capture.usecase.NotifyPictureSaveFailed
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedPreview
import app.grapheneos.camera.domain.sound.usecase.PlayCameraSound
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.model.PictureFailureCause
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderDialog
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderCaptureEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.input.ThumbnailSize
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderCaptureResult
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderCaptureState
import app.grapheneos.camera.util.printStackTraceToString
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
import org.junit.Assert.assertNull
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
    private val playCameraSound = mockk<PlayCameraSound>(relaxed = true)
    private val blinkTimerLight = mockk<BlinkTimerLight>(relaxed = true)

    private val onCaptureEvent = slot<(CapturedImageEvent) -> Unit>()
    private val captureFinished = CompletableDeferred<Unit>()

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.VIDEO)

    @Test
    fun result_isShownUntilDiscarded() {
        runTest {
            val result = ViewfinderCaptureResult.Photo(image = createBitmap(1, 1))
            val delegate = createDelegate()

            delegate.showResult(result)
            assertEquals(result, capture().result)

            delegate.discardResult()
            assertNull(capture().result)
        }
    }

    @Test
    fun onScreenDestroyed_keepsTheResultAndTheHeldShutter() {
        runTest {
            val result = ViewfinderCaptureResult.Photo(image = createBitmap(1, 1))
            val delegate = createDelegate()

            delegate.holdShutter()
            delegate.showResult(result)
            delegate.startSelfTimer()
            delegate.onScreenDestroyed()

            assertEquals(
                ViewfinderCaptureState(result = result, isShutterHeld = true),
                capture(),
            )
        }
    }

    @Test
    fun holdShutter_releasesItAfterASecond() {
        runTest {
            val delegate = createDelegate()

            delegate.holdShutter()
            assertTrue(capture().isShutterHeld)

            advanceTimeBy(999.milliseconds)
            runCurrent()
            assertTrue(capture().isShutterHeld)

            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertFalse(capture().isShutterHeld)
        }
    }

    @Test
    fun takePreviewPicture_handsThePhotoOverWithoutSavingIt() {
        runTest {
            val bitmap = createBitmap(1, 1)
            coEvery { capturePreviewImage() } returns CapturePreviewResult.Captured(bitmap)
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            val events = collectEvents(delegate)

            delegate.takePreviewPicture()

            assertFalse(capture().isSavingPicture)
            assertEquals(listOf(ViewfinderCaptureEvent.PhotoCaptured(bitmap)), events)
            assertEquals(listOf(Effect.ShowMessage(R.string.image_captured_successfully)), effects)
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
    fun confirmResult_withAFileToWriteInto_storesThePhotoThere() {
        runTest {
            val bitmap = createBitmap(1, 1)
            coEvery { storeCapturedPreview(uri = FOREIGN_URI, bitmap = bitmap) } returns true
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            delegate.showResult(ViewfinderCaptureResult.Photo(image = bitmap))

            delegate.confirmResult(outputUri = FOREIGN_URI)

            assertEquals(listOf(Effect.Picture.PreviewStored), effects)
        }
    }

    @Test
    fun confirmResult_thatCannotBeStored_saysSo() {
        runTest {
            val bitmap = createBitmap(1, 1)
            coEvery { storeCapturedPreview(uri = FOREIGN_URI, bitmap = bitmap) } returns false
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            delegate.showResult(ViewfinderCaptureResult.Photo(image = bitmap))

            delegate.confirmResult(outputUri = FOREIGN_URI)

            assertEquals(listOf(Effect.Picture.PreviewStoreFailed), effects)
        }
    }

    @Test
    fun confirmResult_withoutAFile_handsThePhotoBackInline() {
        runTest {
            val bitmap = createBitmap(1, 1)
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            delegate.showResult(ViewfinderCaptureResult.Photo(image = bitmap))

            delegate.confirmResult(outputUri = null)

            assertEquals(listOf(Effect.Picture.PreviewReturned(bitmap = bitmap)), effects)
            coVerify(exactly = 0) { storeCapturedPreview(uri = any(), bitmap = any()) }
        }
    }

    @Test
    fun confirmResult_ofALargePhoto_scalesItDownForTheIntent() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            delegate.showResult(ViewfinderCaptureResult.Photo(image = createBitmap(1000, 1000)))

            delegate.confirmResult(outputUri = null)

            val returned = (effects.single() as Effect.Picture.PreviewReturned).bitmap
            assertEquals(100, returned.width)
            assertEquals(100, returned.height)
        }
    }

    @Test
    fun confirmResult_withoutAPhoto_doesNothing() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            delegate.showResult(ViewfinderCaptureResult.Video(image = null))

            delegate.confirmResult(outputUri = null)

            assertTrue(effects.isEmpty())
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
            assertEquals(listOf(Effect.Preview.Flash(selfIlluminate = false)), effects)
            coVerify(exactly = 1) { playCameraSound(CameraSound.SHUTTER) }
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
    fun aPictureThatCannotBeDecoded_stillFinishesTheSaveWithoutAThumbnail() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)

            delegate.takePicture()
            emitCaptureEvent(CapturedImageEvent.Captured)
            emitCaptureEvent(CapturedImageEvent.ThumbnailReady(thumbnail = null))

            assertFalse(capture().isSavingPicture)
            assertEquals(listOf(ViewfinderCaptureEvent.ThumbnailReady(thumbnail = null)), events)
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
            val cause = IOException("no camera")
            delegate.onScreenStarted()

            delegate.takePicture()
            emitCaptureEvent(
                CapturedImageEvent.CaptureFailed(errorCode = CAPTURE_ERROR_CODE, cause = cause),
            )

            assertFalse(capture().isSavingPicture)
            assertEquals(
                ViewfinderDialog.PictureFailure(
                    cause = PictureFailureCause.Capture(errorCode = CAPTURE_ERROR_CODE),
                    details = "osVersion: ${Build.FINGERPRINT}" +
                        "\npackage: ${BuildConfig.APPLICATION_ID}:${BuildConfig.VERSION_CODE}" +
                        "\n\n${cause.printStackTraceToString()}",
                ),
                stateHolder.state.value.dialog,
            )
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
            assertNull(stateHolder.state.value.dialog)
        }
    }

    @Test
    fun saveFailed_onScreen_reportsTheStageThatFailed() {
        runTest {
            val delegate = createDelegate()
            delegate.onScreenStarted()

            delegate.takePicture()
            emitCaptureEvent(CapturedImageEvent.Captured)
            emitCaptureEvent(saveFailure(alreadyReported = false))

            assertFalse(capture().isSavingPicture)
            val failure = stateHolder.state.value.dialog as ViewfinderDialog.PictureFailure
            assertEquals(PictureFailureCause.Save(stage = SAVE_FAILURE_STAGE), failure.cause)
            coVerify(exactly = 0) { notifyPictureSaveFailed() }
        }
    }

    @Test
    fun saveFailed_alreadyReported_isOnlyMentioned() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            delegate.onScreenStarted()

            delegate.takePicture()
            emitCaptureEvent(CapturedImageEvent.Captured)
            emitCaptureEvent(saveFailure(alreadyReported = true))

            assertEquals(Effect.ShowMessage(R.string.unable_to_save_image), effects.last())
            assertNull(stateHolder.state.value.dialog)
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
            assertNull(stateHolder.state.value.dialog)
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
    fun selfTimer_withTimerLight_blinksOnceThenTwiceAndLeavesTheLastSecondDark() {
        runTest {
            val delegate = createDelegate()
            stateHolder.update {
                it.copy(settings = CameraSettings(selfTimerDuration = 5.seconds, timerLight = true))
            }

            delegate.startSelfTimer()
            advanceTimeBy(5.seconds)
            runCurrent()

            coVerifyOrder {
                blinkTimerLight(1)
                blinkTimerLight(1)
                blinkTimerLight(2)
                blinkTimerLight(2)
            }
            coVerify(exactly = 4) { blinkTimerLight(any()) }
        }
    }

    @Test
    fun selfTimer_withoutTimerLight_neverBlinks() {
        runTest {
            val delegate = createDelegate()
            stateHolder.update {
                it.copy(settings = CameraSettings(selfTimerDuration = SELF_TIMER))
            }

            delegate.startSelfTimer()
            advanceTimeBy(SELF_TIMER)
            runCurrent()

            coVerify(exactly = 0) { blinkTimerLight(any()) }
        }
    }

    @Test
    fun selfTimer_countsDownOneSecondApartAndThenFinishes() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)
            val timeline = mutableListOf<Pair<Duration?, Long>>()
            stateHolder.update {
                it.copy(settings = CameraSettings(selfTimerDuration = SELF_TIMER))
            }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                stateHolder.state.collect { state ->
                    timeline += state.capture.selfTimerRemaining to currentTime
                }
            }

            delegate.startSelfTimer()

            advanceTimeBy(SELF_TIMER)
            runCurrent()

            assertEquals(
                listOf(
                    null to 0L,
                    3.seconds to 0L,
                    2.seconds to 1_000L,
                    1.seconds to 2_000L,
                    null to 3_000L,
                ),
                timeline,
            )
            assertEquals(listOf(ViewfinderCaptureEvent.SelfTimerFinished), events)
            coVerifyOrder {
                playCameraSound(CameraSound.TIMER_TICK)
                playCameraSound(CameraSound.TIMER_TICK)
                playCameraSound(CameraSound.TIMER_FINAL_SECOND)
            }
        }
    }

    @Test
    fun selfTimer_isRunningUntilCancelled() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)
            stateHolder.update {
                it.copy(settings = CameraSettings(selfTimerDuration = SELF_TIMER))
            }

            delegate.startSelfTimer()
            assertEquals(SELF_TIMER, capture().selfTimerRemaining)

            delegate.cancelSelfTimer()
            advanceTimeBy(SELF_TIMER)
            runCurrent()

            assertNull(capture().selfTimerRemaining)
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun selfTimerCancelled_withoutACountdown_putsNothingBack() {
        runTest {
            val delegate = createDelegate()
            val states = mutableListOf<ViewfinderCaptureState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                stateHolder.state.collect { state -> states += state.capture }
            }

            delegate.cancelSelfTimer()

            assertEquals(listOf(ViewfinderCaptureState()), states)
        }
    }

    @Test
    fun screenDestroyed_dropsTheCountdownWithoutFinishingIt() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)
            stateHolder.update {
                it.copy(settings = CameraSettings(selfTimerDuration = SELF_TIMER))
            }

            delegate.startSelfTimer()
            delegate.onScreenDestroyed()

            advanceTimeBy(SELF_TIMER)
            runCurrent()

            assertNull(capture().selfTimerRemaining)
            assertTrue(events.isEmpty())
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
            playCameraSound = playCameraSound,
            blinkTimerLight = blinkTimerLight,
            applicationScope = backgroundScope,
            mainDispatcher = UnconfinedTestDispatcher(testScheduler),
            defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
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
        const val STORAGE_LOCATION = "MediaStore"
        const val CAPTURE_ERROR_CODE = 2
        const val SAVE_FAILURE_STAGE = "FILE_WRITE"

        val FOREIGN_URI: Uri = Uri.parse("content://com.example.app/images/1")
        val ITEM = CapturedItem(CapturedItemType.IMAGE, "20260920_120000_000", Uri.EMPTY)
        val SELF_TIMER = 3.seconds
    }
}
