package app.grapheneos.camera

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.util.getParcelableExtra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditMediaRegressionTest {

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
     * Regression test for the production crash: startActivity() computes the uri grant for a
     * directly started editor and throws a SecurityException when the app has lost access to
     * the item, and the exception went uncaught through the edit path.
     */
    @Test
    @UiThreadTest
    fun edit_whenUriGrantIsDenied_reportsFailureInsteadOfCrashing() {
        val host = GrantDenyingHostActivity().attached()

        assertEquals(
            R.string.unable_to_edit_media,
            editCapturedItem(host, staleItem, useDefaultEditor = true),
        )
    }

    @Test
    @UiThreadTest
    fun editWith_whenUriGrantIsDenied_reportsFailureInsteadOfCrashing() {
        val host = GrantDenyingHostActivity().attached()

        assertEquals(
            R.string.unable_to_edit_media,
            editCapturedItem(host, staleItem, useDefaultEditor = false),
        )
    }

    @Test
    @UiThreadTest
    fun edit_whenNoEditorIsInstalled_reportsMissingEditor() {
        val host = NoEditorHostActivity().attached()

        assertEquals(
            R.string.no_editor_app_error,
            editCapturedItem(host, staleItem, useDefaultEditor = true),
        )
    }

    @Test
    @UiThreadTest
    fun edit_launchesEditorWithItemUri() {
        val host = RecordingHostActivity().attached()

        assertNull(editCapturedItem(host, staleItem, useDefaultEditor = true))

        val edit = host.launched
        assertNotNull(edit)
        assertEquals(Intent.ACTION_EDIT, edit!!.action)
        assertEquals(staleItem.uri, edit.data)
        assertEquals(staleItem.mimeType(), edit.type)
        assertEquals(staleItem.uri, getParcelableExtra<Uri>(edit, Intent.EXTRA_STREAM))
        assertTrue(edit.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    @UiThreadTest
    fun editWith_launchesChooserWithEditIntent() {
        val host = RecordingHostActivity().attached()

        assertNull(editCapturedItem(host, staleItem, useDefaultEditor = false))

        val chooser = host.launched
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)
        assertFalse(chooser.getBooleanExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, true))

        val edit = getParcelableExtra<Intent>(chooser, Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_EDIT, edit.action)
        assertEquals(staleItem.uri, edit.data)
        assertEquals(staleItem.mimeType(), edit.type)
        assertTrue(edit.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    abstract class HostActivity : Activity() {
        fun attach(base: Context) = attachBaseContext(base)
    }

    /**
     * Behaves like the platform's server-side grant check inside startActivity(): it throws a
     * SecurityException when the calling app cannot itself access the uri it is granting.
     */
    private class GrantDenyingHostActivity : HostActivity() {
        override fun startActivity(intent: Intent?) {
            throw SecurityException(
                "UID 10999 does not have permission to uri 0 @ content://media/external/images/media/987654321"
            )
        }
    }

    private class NoEditorHostActivity : HostActivity() {
        override fun startActivity(intent: Intent?) {
            throw ActivityNotFoundException("No Activity found to handle Intent")
        }
    }

    /** Captures the launched intent instead of starting an editor. */
    private class RecordingHostActivity : HostActivity() {
        var launched: Intent? = null

        override fun startActivity(intent: Intent?) {
            launched = intent
        }
    }
}
