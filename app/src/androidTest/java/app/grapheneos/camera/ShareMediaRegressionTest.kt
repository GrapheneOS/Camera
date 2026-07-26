package app.grapheneos.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.util.getParcelableExtra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareMediaRegressionTest {

    private val staleItem = CapturedItem(
        ITEM_TYPE_IMAGE,
        "20260724_120000",
        Uri.parse("content://media/external/images/media/987654321"),
    )

    private fun <T : HostActivity> T.attached(): T {
        attach(InstrumentationRegistry.getInstrumentation().targetContext)
        return this
    }

    /**
     * Regression test for the production crash: OEM builds that grant the shared uri inside
     * startActivity() throw a SecurityException when the app has lost access to the item, and
     * the exception went uncaught through the share path.
     */
    @Test
    @UiThreadTest
    fun share_whenUriGrantIsDenied_reportsFailureInsteadOfCrashing() {
        val host = OemPreGrantHostActivity().attached()

        assertEquals(R.string.unable_to_share_media, shareCapturedItem(host, staleItem))
    }

    @Test
    @UiThreadTest
    fun share_launchesChooserWithItemUri() {
        val host = ChooserRecordingHostActivity().attached()

        assertNull(shareCapturedItem(host, staleItem))

        val chooser = host.launched
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)

        val send = getParcelableExtra<Intent>(chooser, Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(staleItem.uri, getParcelableExtra<Uri>(send, Intent.EXTRA_STREAM))
        assertEquals(staleItem.mimeType(), send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    abstract class HostActivity : Activity() {
        fun attach(base: Context) = attachBaseContext(base)
    }

    /**
     * Behaves like OEM frameworks that eagerly grant the shared uri to the target inside
     * startActivity(): the grant fails with a SecurityException when the sharing app cannot
     * access the uri itself.
     */
    private class OemPreGrantHostActivity : HostActivity() {
        override fun startActivity(intent: Intent?) {
            throw SecurityException(
                "UID 10999 does not have permission to uri 0 @ content://media/external/images/media/987654321"
            )
        }
    }

    /** Captures the launched intent instead of opening the system chooser. */
    private class ChooserRecordingHostActivity : HostActivity() {
        var launched: Intent? = null

        override fun startActivity(intent: Intent?) {
            launched = intent
        }
    }
}
