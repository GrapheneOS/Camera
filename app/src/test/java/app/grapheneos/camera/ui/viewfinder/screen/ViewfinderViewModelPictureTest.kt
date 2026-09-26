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
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
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
    fun capturedPreviewDismissed_forgetsThePreviewBeforeRebinding() {
        runTest {
            every { cameraDelegate.canBeginBind(forced = true) } returns true

            val viewModel = createViewModel()
            viewModel.onAction(CaptureAction.CapturedPreviewDismissed)

            verifyOrder {
                captureDelegate.dismissCapturedPreview()
                cameraDelegate.canBeginBind(forced = true)
            }
        }
    }

    @Test
    fun capturedPreviewShown_isRecordedAndReleasesTheCamera() {
        runTest {
            val viewModel = createViewModel()
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
