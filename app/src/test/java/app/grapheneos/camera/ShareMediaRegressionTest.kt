package app.grapheneos.camera

import android.app.Activity
import android.content.Intent
import android.net.Uri
import app.grapheneos.camera.util.getParcelableExtra
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareMediaRegressionTest {

    private val activity = mockk<Activity>(relaxed = true)

    private val staleItem = CapturedItem(
        ITEM_TYPE_IMAGE,
        "20260724_120000",
        Uri.parse("content://media/external/images/media/987654321"),
    )

    /**
     * Regression test for the production crash: OEM builds that grant the shared uri inside
     * startActivity() throw a SecurityException when the app has lost access to the item, and
     * the exception went uncaught through the share path.
     */
    @Test
    fun share_whenUriGrantIsDenied_reportsFailureInsteadOfCrashing() {
        every { activity.startActivity(any()) } throws SecurityException(GRANT_DENIED)

        assertEquals(R.string.unable_to_share_media, shareCapturedItem(activity, staleItem))
    }

    @Test
    fun share_launchesChooserWithItemUri() {
        every { activity.getString(R.string.share_image) } returns CHOOSER_TITLE

        assertNull(shareCapturedItem(activity, staleItem))

        val chooser = startedIntent()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(CHOOSER_TITLE, chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))

        val send = getParcelableExtra<Intent>(chooser, Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(staleItem.uri, getParcelableExtra<Uri>(send, Intent.EXTRA_STREAM))
        assertEquals(staleItem.mimeType(), send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    private fun startedIntent(): Intent {
        val startedIntent = slot<Intent>()
        verify(exactly = 1) { activity.startActivity(capture(startedIntent)) }
        return startedIntent.captured
    }

    private companion object {
        private const val GRANT_DENIED =
            "UID 10999 does not have permission to uri 0 @ content://media/external/images/media/987654321"
        private const val CHOOSER_TITLE = "Share via"
    }
}
