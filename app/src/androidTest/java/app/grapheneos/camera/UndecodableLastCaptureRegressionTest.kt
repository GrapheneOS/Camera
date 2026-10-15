package app.grapheneos.camera

import android.Manifest
import android.provider.DocumentsContract
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A last capture that failed to decode had every capture listed to learn whether it was gone, on
 * each resume, though a lookup of its own uri finds it still there.
 */
@RunWith(AndroidJUnit4::class)
class UndecodableLastCaptureRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    @get:Rule
    val captures = TestCaptures()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun undecodableCapture_stillThere_isPresent() {
        val last = insertUndecodableVideo("20991231_235959")

        assertTrue(CapturedItems.isPresent(context, last))
    }

    @Test
    fun capture_afterItIsDeleted_isNotPresent() {
        val last = captures.insertImage("20991231_235959")
        captures.delete(last)

        assertFalse(CapturedItems.isPresent(context, last))
    }

    @Test
    fun capture_inAFolderThatIsAway_isNotPresent() {
        val tree = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents", "0000-0000:DCIM"
        )
        val last = CapturedItem(
            ITEM_TYPE_IMAGE,
            "20991231_235959",
            DocumentsContract.buildDocumentUriUsingTree(
                tree, "0000-0000:DCIM/${IMAGE_NAME_PREFIX}20991231_235959.jpg"
            ),
        )

        // Left for a listing to tell apart from gone
        assertFalse(CapturedItems.isPresent(context, last))
    }

    @Test
    fun savedLastCapture_thatFailsToDecode_isKeptWithoutAListing() {
        captures.insertImage("20991231_235958")
        // Named so that a listing would miss it and replace it with the image before
        val last = insertUndecodableVideo("20991231_235959", name = "unlisted.mp4")
        captures.save(last)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitThumbnailLoader(scenario)
            scenario.onActivity {
                assertFalse(it.isThumbnailLoaded)
                assertEquals(last.uri, it.camConfig.lastCapturedItem?.uri)
            }
        }
        assertEquals(last.uri.toString(), captures.savedUri)
    }

    private fun insertUndecodableVideo(
        dateString: String,
        name: String = "$VIDEO_NAME_PREFIX$dateString.mp4",
    ): CapturedItem {
        val item = captures.insertVideo(dateString, name)
        // A file to find, which no thumbnail can be made of
        context.contentResolver.openOutputStream(item.uri)!!.use {
            it.write("not a video".toByteArray())
        }
        return item
    }
}
