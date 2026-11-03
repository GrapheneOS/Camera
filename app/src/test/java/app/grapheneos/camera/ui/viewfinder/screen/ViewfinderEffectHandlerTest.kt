package app.grapheneos.camera.ui.viewfinder.screen

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Vibrator
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect.CaptureResult
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderEffectHandlerTest {

    private val activity = mockk<MainActivity>(relaxed = true)
    private val handler = ViewfinderEffectHandlerImpl(
        activity = activity,
        vibrator = mockk<Vibrator>(),
        onAction = {},
    )

    @Test
    fun storedPhoto_returnsSuccessAndFinishes() {
        handler.handle(CaptureResult.PhotoStored)

        verifyOrder {
            activity.setResult(Activity.RESULT_OK)
            activity.finish()
        }
    }

    @Test
    fun failedPhotoStore_returnsCancellationAndFinishes() {
        handler.handle(CaptureResult.PhotoStoreFailed)

        verifyOrder {
            activity.showMessage(R.string.unable_to_save_image)
            activity.setResult(Activity.RESULT_CANCELED)
            activity.finish()
        }
    }

    @Test
    fun failedPhotoCapture_returnsCancellationAndFinishes() {
        handler.handle(CaptureResult.PhotoFailed)

        verifyOrder {
            activity.showMessage(R.string.unable_to_capture_image)
            activity.setResult(Activity.RESULT_CANCELED)
            activity.finish()
        }
    }

    @Test
    fun inlinePhoto_returnsTheBitmapWithTheCaptureIntentContract() {
        val photo = createBitmap(1, 1)
        val result = slot<Intent>()

        handler.handle(CaptureResult.PhotoReturned(photo))

        verifyOrder {
            activity.setResult(Activity.RESULT_OK, capture(result))
            activity.finish()
        }
        assertEquals("inline-data", result.captured.action)
        assertSame(photo, result.captured.getParcelableExtra("data", Bitmap::class.java))
    }

    @Test
    fun returnedVideo_grantsReadAccessAndIncludesTheOutputUri() {
        val uri = Uri.parse("content://media/external/video/media/42")
        val result = slot<Intent>()

        handler.handle(CaptureResult.VideoReturned(uri))

        verifyOrder {
            activity.setResult(Activity.RESULT_OK, capture(result))
            activity.finish()
        }
        assertEquals(uri, result.captured.data)
        assertEquals(uri, result.captured.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java))
        assertTrue(result.captured.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun videoReview_usesTheActivityLauncher() {
        val uri = Uri.parse("content://media/external/video/media/42")

        handler.handle(CaptureResult.OpenVideo(uri))

        verify(exactly = 1) { activity.videoPlayerLauncher.open(activity, uri) }
        verify(exactly = 0) { activity.finish() }
    }
}
