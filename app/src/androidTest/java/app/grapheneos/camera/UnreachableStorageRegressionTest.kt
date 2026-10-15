package app.grapheneos.camera

import android.Manifest
import android.content.ContentUris
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A last capture that could not be loaded or listed was taken to be deleted, though both fail while
 * its storage is out of reach. The capture before it, or none, was then saved in its place, which
 * kept the thumbnail on that, or blank, once the storage was back.
 */
@RunWith(AndroidJUnit4::class)
class UnreachableStorageRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    @get:Rule
    val captures = TestCaptures()

    @Test
    fun captureInTheMediaStore_whileItCannotBeListed_isNotTakenAsGone() {
        val last = CapturedItem(
            ITEM_TYPE_IMAGE,
            "20991231_235959",
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 1),
        )
        // What a MediaStore that is down for a moment lists
        val listing = CapturedItemsListing(
            files = emptyList(),
            listedMediaStore = false,
            unlistedTrees = emptyList(),
        )

        assertFalse(listing.lacks(last))
        assertFalse(listing.isComplete)
    }

    @Test
    fun savedLastCapture_inAFolderThatIsAway_isKept() {
        captures.insertImage("20991231_235958")
        val inFolder = CapturedItem(
            ITEM_TYPE_IMAGE,
            "20991231_235959",
            DocumentsContract.buildDocumentUriUsingTree(
                AWAY_TREE, "$AWAY_TREE_ID/${IMAGE_NAME_PREFIX}20991231_235959.jpg"
            ),
        )
        captures.track(AWAY_TREE)
        captures.save(inFolder)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitThumbnailLoader(scenario)
            scenario.onActivity { assertEquals(inFolder.uri, it.camConfig.lastCapturedItem?.uri) }
        }
        assertEquals(inFolder.uri.toString(), captures.savedUri)
    }

    @Test
    fun savedLastCapture_deletedWhileAFolderIsAway_isNotReplaced() {
        val older = captures.insertImage("20991231_235958")
        val newest = captures.insertImage("20991231_235959")
        captures.track(AWAY_TREE)
        captures.save(newest)
        captures.delete(newest)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "the thumbnail shows the capture before") {
                it.isThumbnailLoaded && it.camConfig.lastCapturedItem == older
            }
        }
        assertEquals(newest.uri.toString(), captures.savedUri)
    }

    private companion object {
        // A drive that isn't there, with no grant to it
        const val AWAY_TREE_ID = "0000-0000:DCIM"
        val AWAY_TREE: Uri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents", AWAY_TREE_ID
        )
    }
}
