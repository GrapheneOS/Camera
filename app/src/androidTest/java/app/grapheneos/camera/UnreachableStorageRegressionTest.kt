package app.grapheneos.camera

import android.Manifest
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import java.io.FileNotFoundException
import java.util.concurrent.ExecutorService
import org.junit.Assert.assertEquals
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

    private class DownProvider : ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<String>?,
            selection: String?,
            selectionArgs: Array<String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            throw FileNotFoundException("No content provider: $uri")

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<String>?,
        ) = 0
    }

    private class MediaProviderDownContext(base: Context) : ContextWrapper(base) {
        private val resolver = ContentResolver.wrap(DownProvider())

        override fun getContentResolver(): ContentResolver = resolver
    }

    @Test
    fun thumbnail_afterTheMediaProviderWasDown_showsTheLastCaptureAgain() {
        val last = captures.insertImage("20991231_235959")
        captures.save(last)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "the thumbnail is loaded") {
                it.isThumbnailLoaded && it.camConfig.lastCapturedItem == last
            }

            scenario.onActivity {
                it.mediaContext = MediaProviderDownContext(it.applicationContext)
                it.updateThumbnail()
            }
            awaitThumbnailLoader(scenario)
            scenario.onActivity { assertEquals(last, it.camConfig.lastCapturedItem) }
            assertEquals(last.uri.toString(), captures.savedUri)

            scenario.onActivity {
                it.mediaContext = it.applicationContext
                it.updateThumbnail()
            }
            waitUntil(scenario, "the thumbnail shows the last capture again") {
                it.isThumbnailLoaded && it.camConfig.lastCapturedItem == last
            }
        }
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

    private fun awaitThumbnailLoader(scenario: ActivityScenario<MainActivity>) {
        lateinit var loader: ExecutorService
        scenario.onActivity { loader = it.thumbnailLoaderExecutor }
        loader.submit {}.get()
        // Runs after the result the loader posted
        scenario.onActivity {}
    }

    private companion object {
        // A drive that isn't there, with no grant to it
        const val AWAY_TREE_ID = "0000-0000:DCIM"
        val AWAY_TREE: Uri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents", AWAY_TREE_ID
        )
    }
}
