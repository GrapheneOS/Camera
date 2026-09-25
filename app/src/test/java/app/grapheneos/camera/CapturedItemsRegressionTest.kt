package app.grapheneos.camera

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import app.grapheneos.camera.util.getParcelableExtra
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CapturedItemsRegressionTest {

    private val activity = mockk<Activity>(relaxed = true)

    private val staleItem = CapturedItem(
        ITEM_TYPE_IMAGE,
        "20260724_120000",
        Uri.parse("content://media/external/images/media/987654321"),
    )

    /**
     * Regression test for the production crash: startActivity() computes the uri grant for a
     * directly started editor and throws a SecurityException when the app has lost access to
     * the item, and the exception went uncaught through the edit path.
     */
    @Test
    fun edit_whenUriGrantIsDenied_reportsFailureInsteadOfCrashing() {
        every { activity.startActivity(any()) } throws SecurityException(GRANT_DENIED)

        assertEquals(
            R.string.unable_to_edit_media,
            editCapturedItem(activity, staleItem, useDefaultEditor = true),
        )
    }

    @Test
    fun editWith_whenUriGrantIsDenied_reportsFailureInsteadOfCrashing() {
        every { activity.startActivity(any()) } throws SecurityException(GRANT_DENIED)

        assertEquals(
            R.string.unable_to_edit_media,
            editCapturedItem(activity, staleItem, useDefaultEditor = false),
        )
    }

    @Test
    fun edit_whenNoEditorIsInstalled_reportsMissingEditor() {
        every { activity.startActivity(any()) } throws ActivityNotFoundException(NO_EDITOR)

        assertEquals(
            R.string.no_editor_app_error,
            editCapturedItem(activity, staleItem, useDefaultEditor = true),
        )
    }

    @Test
    fun edit_launchesEditorWithItemUri() {
        assertNull(editCapturedItem(activity, staleItem, useDefaultEditor = true))

        val edit = startedIntent()
        assertEquals(Intent.ACTION_EDIT, edit.action)
        assertEquals(staleItem.uri, edit.data)
        assertEquals(staleItem.mimeType(), edit.type)
        assertEquals(staleItem.uri, getParcelableExtra<Uri>(edit, Intent.EXTRA_STREAM))
        assertTrue(edit.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun editWith_launchesChooserWithEditIntent() {
        every { activity.getString(R.string.edit_image) } returns EDIT_CHOOSER_TITLE

        assertNull(editCapturedItem(activity, staleItem, useDefaultEditor = false))

        val chooser = startedIntent()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(EDIT_CHOOSER_TITLE, chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))
        assertFalse(chooser.getBooleanExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, true))

        val edit = getParcelableExtra<Intent>(chooser, Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_EDIT, edit.action)
        assertEquals(staleItem.uri, edit.data)
        assertEquals(staleItem.mimeType(), edit.type)
        assertTrue(edit.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

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
        every { activity.getString(R.string.share_image) } returns SHARE_CHOOSER_TITLE

        assertNull(shareCapturedItem(activity, staleItem))

        val chooser = startedIntent()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(SHARE_CHOOSER_TITLE, chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))

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
        const val GRANT_DENIED =
            "UID 10999 does not have permission to uri 0 @ content://media/external/images/media/987654321"
        const val NO_EDITOR = "No Activity found to handle Intent"
        const val EDIT_CHOOSER_TITLE = "Edit with"
        const val SHARE_CHOOSER_TITLE = "Share via"
    }
}
