package app.grapheneos.camera.ui.viewfinder.screen

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.createBitmap
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.R
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.model.ImageSaverException
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelPictureTest : ViewfinderViewModelTestBase() {

    @Test
    fun capturedPreviewDismissed_forgetsThePreviewBeforeRebinding() {
        runTest {
            every { cameraDelegate.canBeginBind(forced = true) } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(LifecycleAction.CapturedPreviewDismissed)

            verifyOrder {
                captureDelegate.dismissCapturedPreview()
                cameraDelegate.canBeginBind(forced = true)
            }
        }
    }

    @Test
    fun capturedPreviewShown_isRecordedAndReleasesTheCamera() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(CaptureAction.CapturedPreviewShown)

            verifyOrder {
                captureDelegate.showCapturedPreview()
                cameraDelegate.unbindCamera()
            }
        }
    }

    @Test
    fun capturedPreviewConfirmed_writesWhereTheCallerAsked() {
        runTest {
            val viewModel = createViewModel(
                applicationScope = backgroundScope,
                outputUri = OUTPUT_URI,
            )
            val bitmap = createBitmap(1, 1)

            viewModel.onAction(CaptureAction.CapturedPreviewConfirmed(bitmap))

            verify(exactly = 1) {
                captureDelegate.confirmPreviewPicture(bitmap = bitmap, outputUri = OUTPUT_URI)
            }
        }
    }

    @Test
    fun shutterClicked_withTheCameraReady_takesThePicture() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(session = it.session.copy(canTakePicture = true)) }

            viewModel.onAction(CaptureAction.ShutterClicked)

            verify(exactly = 1) { captureDelegate.takePicture() }
        }
    }

    @Test
    fun shutterClicked_inACaptureSession_takesAPictureToHandBack() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true

            val viewModel = createViewModel(
                applicationScope = backgroundScope,
                entryPoint = cameraEntryPoint(isCaptureSession = true),
            )
            stateHolder.update { it.copy(session = it.session.copy(canTakePicture = true)) }

            viewModel.onAction(CaptureAction.ShutterClicked)

            verify(exactly = 1) { captureDelegate.takePreviewPicture() }
            verify(exactly = 0) { captureDelegate.takePicture() }
        }
    }

    @Test
    fun previewCaptured_showsItAndStopsTheLoader() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            val bitmap = createBitmap(1, 1)

            captureEvents.emit(CapturedImageEvent.PreviewCaptured(bitmap = bitmap))

            verify(exactly = 1) { captureDelegate.finishPictureSave() }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.Picture.PreviewCaptured(bitmap),
                    ViewfinderScreenEffect.ShowMessage(R.string.image_captured_successfully),
                ),
                effects,
            )
        }
    }

    @Test
    fun previewFailed_reportsItAndStopsTheLoader() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            captureEvents.emit(CapturedImageEvent.PreviewFailed)

            verify(exactly = 1) { captureDelegate.finishPictureSave() }
            assertEquals(listOf(ViewfinderScreenEffect.Picture.PreviewFailed), effects)
        }
    }

    @Test
    fun shutterClicked_whileTheCameraCannotCapture_saysSoAndTakesNothing() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(CaptureAction.ShutterClicked)

            verify(exactly = 0) { captureDelegate.takePicture() }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.ShowMessage(
                        R.string.unsupported_taking_picture_while_recording,
                    ),
                ),
                effects,
            )
        }
    }

    @Test
    fun shutterClicked_whileACaptureIsInFlight_takesNothing() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(
                    session = it.session.copy(canTakePicture = true),
                    capture = it.capture.copy(isTakingPicture = true),
                )
            }

            viewModel.onAction(CaptureAction.ShutterClicked)

            verify(exactly = 0) { captureDelegate.takePicture() }
        }
    }

    @Test
    fun captured_startsSavingItAndSoundsTheShutterBeforeFlashingThePreview() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            captureEvents.emit(CapturedImageEvent.Captured)

            verify(exactly = 1) { captureDelegate.startPictureSave() }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.Picture.Captured,
                    ViewfinderScreenEffect.FlashPreview(selfIlluminate = false),
                ),
                effects,
            )
        }
    }

    @Test
    fun thumbnailReady_showsItAndFinishesTheSave() {
        runTest {
            createViewModel(applicationScope = backgroundScope)

            captureEvents.emit(CapturedImageEvent.ThumbnailReady(thumbnail = THUMBNAIL))

            verify(exactly = 1) { captureDelegate.finishPictureSave() }
            verify(exactly = 1) { galleryDelegate.showThumbnail(THUMBNAIL) }
        }
    }

    @Test
    fun locationUnavailable_saysSo() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            captureEvents.emit(CapturedImageEvent.LocationUnavailable)

            assertEquals(
                listOf(ViewfinderScreenEffect.ShowMessage(R.string.location_unavailable)),
                effects,
            )
        }
    }

    @Test
    fun captureFailed_reportsTheFailureAndEndsBothStages() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            val cause = IOException("no camera")

            captureEvents.emit(
                CapturedImageEvent.CaptureFailed(errorCode = CAPTURE_ERROR_CODE, cause = cause),
            )

            verify(exactly = 1) { captureDelegate.finishPictureSave() }

            val failure = effects.single() as ViewfinderScreenEffect.Picture.CaptureFailed
            assertEquals(CAPTURE_ERROR_CODE, failure.errorCode)
            assertEquals(cause.javaClass.name, failure.details.name)
        }
    }

    @Test
    fun saveFailed_reportsTheStageThatFailed() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            captureEvents.emit(
                CapturedImageEvent.SaveFailed(
                    cause = ImageSaverException(ImageSaverException.Place.FILE_WRITE),
                    alreadyReported = true,
                ),
            )

            verify(exactly = 1) { captureDelegate.finishPictureSave() }

            val failure = effects.single() as ViewfinderScreenEffect.Picture.SaveFailed
            assertEquals(SAVE_FAILURE_STAGE, failure.stage)
            assertTrue(failure.alreadyReported)
        }
    }

    @Test
    fun saved_recordsTheItem() {
        runTest {
            createViewModel(applicationScope = backgroundScope)
            val item = CapturedItem(ITEM_TYPE_IMAGE, "20260920_120000_000", Uri.EMPTY)

            captureEvents.emit(CapturedImageEvent.Saved(item = item))

            verify(exactly = 1) { galleryDelegate.recordCapturedItem(item) }
        }
    }

    private companion object {
        const val CAPTURE_ERROR_CODE = 2
        const val SAVE_FAILURE_STAGE = "FILE_WRITE"

        val THUMBNAIL: Bitmap = createBitmap(1, 1)
        val OUTPUT_URI: Uri = Uri.parse("content://com.example.app/images/1")
    }
}
