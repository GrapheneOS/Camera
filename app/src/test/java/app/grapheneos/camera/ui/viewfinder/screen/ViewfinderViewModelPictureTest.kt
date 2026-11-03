package app.grapheneos.camera.ui.viewfinder.screen

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.createBitmap
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderCaptureEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderRecordingEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderCaptureResult
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelPictureTest : ViewfinderViewModelTestBase() {

    @Test
    fun retakeClicked_discardsTheResultBeforeRebinding() {
        runTest {
            every { cameraDelegate.canBeginBind(forced = true) } returns true

            val viewModel = createViewModel()
            viewModel.onAction(CaptureAction.RetakeClicked)

            verifyOrder {
                captureDelegate.discardResult()
                cameraDelegate.canBeginBind(forced = true)
            }
        }
    }

    @Test
    fun photoCaptured_isShownAsTheResultAndReleasesTheCamera() {
        runTest {
            val photo = createBitmap(1, 1)
            createViewModel()

            captureEvents.emit(ViewfinderCaptureEvent.PhotoCaptured(photo = photo))

            verifyOrder {
                captureDelegate.showResult(ViewfinderCaptureResult.Photo(image = photo))
                cameraDelegate.unbindCamera()
            }
        }
    }

    @Test
    fun captureResultSaved_showsTheRecordingOverItsLastFrameAndReleasesTheCamera() {
        runTest {
            val frame = createBitmap(1, 1)
            every { cameraDelegate.currentPreviewFrame() } returns frame
            createViewModel()

            recordingEvents.emit(ViewfinderRecordingEvent.CaptureResultSaved(uri = OUTPUT_URI))

            verifyOrder {
                captureDelegate.showResult(
                    ViewfinderCaptureResult.Video(uri = OUTPUT_URI, image = frame),
                )
                cameraDelegate.unbindCamera()
            }
        }
    }

    @Test
    fun playResultClicked_playsTheResult() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(CaptureAction.PlayResultClicked)

            verify(exactly = 1) { captureDelegate.playResult() }
        }
    }

    @Test
    fun confirmClicked_writesWhereTheCallerAsked() {
        runTest {
            val viewModel = createViewModel(outputUri = OUTPUT_URI)
            viewModel.onAction(CaptureAction.ConfirmClicked)

            verify(exactly = 1) { captureDelegate.confirmResult(outputUri = OUTPUT_URI) }
        }
    }

    @Test
    fun aCaptureSession_holdsTheShutterFromTheStart() {
        runTest {
            createViewModel(entryPoint = cameraEntryPoint(isCaptureSession = true))

            verify(exactly = 1) { captureDelegate.holdShutter() }
        }
    }

    @Test
    fun aRegularSession_leavesTheShutterFree() {
        runTest {
            createViewModel()

            verify(exactly = 0) { captureDelegate.holdShutter() }
        }
    }

    @Test
    fun shutterClicked_withTheCameraReady_takesThePicture() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            val viewModel = createViewModel()
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
                entryPoint = cameraEntryPoint(isCaptureSession = true),
            )
            stateHolder.update { it.copy(session = it.session.copy(canTakePicture = true)) }

            viewModel.onAction(CaptureAction.ShutterClicked)

            verify(exactly = 1) { captureDelegate.takePreviewPicture() }
            verify(exactly = 0) { captureDelegate.takePicture() }
        }
    }

    @Test
    fun shutterClicked_whileTheCameraCannotCapture_saysSoAndTakesNothing() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            val viewModel = createViewModel()
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
            val viewModel = createViewModel()
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
    fun selfTimerFinished_takesThePicture() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            createViewModel()
            stateHolder.update { it.copy(session = it.session.copy(canTakePicture = true)) }

            captureEvents.emit(ViewfinderCaptureEvent.SelfTimerFinished)

            verify(exactly = 1) { captureDelegate.takePicture() }
        }
    }

    @Test
    fun thumbnailReady_showsIt() {
        runTest {
            createViewModel()

            captureEvents.emit(ViewfinderCaptureEvent.ThumbnailReady(thumbnail = THUMBNAIL))

            verify(exactly = 1) { galleryDelegate.showThumbnail(THUMBNAIL) }
        }
    }

    @Test
    fun saved_recordsTheItem() {
        runTest {
            createViewModel()
            val item = CapturedItem(
                type = CapturedItemType.IMAGE,
                dateString = "20260920_120000_000",
                uri = Uri.EMPTY,
            )

            captureEvents.emit(ViewfinderCaptureEvent.Saved(item = item))

            verify(exactly = 1) { galleryDelegate.recordCapturedItem(item) }
        }
    }

    @Test
    fun storageLocationNotFound_whileSaving_isHandedToTheGallery() {
        runTest {
            createViewModel()

            captureEvents.emit(ViewfinderCaptureEvent.StorageLocationNotFound)

            verify(exactly = 1) { galleryDelegate.onStorageLocationNotFound() }
        }
    }

    @Test
    fun screenStartedAndStopped_reachTheCapture() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(LifecycleAction.ScreenStarted)
            viewModel.onAction(LifecycleAction.ScreenStopped)

            verifyOrder {
                captureDelegate.onScreenStarted()
                captureDelegate.onScreenStopped()
            }
        }
    }

    private companion object {
        val THUMBNAIL: Bitmap = createBitmap(1, 1)
        val OUTPUT_URI: Uri = Uri.parse("content://com.example.app/images/1")
    }
}
