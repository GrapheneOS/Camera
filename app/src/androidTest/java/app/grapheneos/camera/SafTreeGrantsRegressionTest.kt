package app.grapheneos.camera

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.media.repository.CapturedItemRepositoryImpl
import app.grapheneos.camera.data.media.store.MediaPrefs
import app.grapheneos.camera.data.media.store.StoragePrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A directory the user picks to save captures in is granted to the app persistably, and the grant
 * used to outlive the app's own record of it: once enough other directories had been picked to push
 * one off the tracked list, nothing released it and the app kept indefinite read/write access to a
 * folder it no longer had any use for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SafTreeGrantsRegressionTest {

    private val authority = "com.android.externalstorage.documents"

    private val readAndWrite =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    private fun tree(name: String): Uri {
        return DocumentsContract.buildTreeDocumentUri(authority, "primary:$name")
    }

    private val context: Context = InstrumentationRegistry
        .getInstrumentation()
        .targetContext
        .applicationContext

    private fun session(): CapturedItemRepositoryImpl {
        return CapturedItemRepositoryImpl(
            storagePrefs = InMemoryDataStore(StoragePrefs()),
            mediaPrefs = InMemoryDataStore(MediaPrefs()),
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    /** The write the app makes when the user picks a directory to save captures in. */
    private fun pickStorageLocation(session: CapturedItemRepositoryImpl, treeUri: Uri) {
        runBlocking { session.setStorageLocation(treeUri.toString()) }
    }

    /** The regression itself: the directory pushed off the tracked list is the one to release. */
    @Test
    fun theTreeThatFallsOffTheTrackedListIsReleased() {
        val session = session()
        val picked = (0..CapturedItems.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES + 1)
            .map { tree("dir$it") }
        picked.forEach { pickStorageLocation(session, it) }

        val tracked = runBlocking { session.trackedSafTrees() }
        assertEquals(CapturedItems.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES + 1, tracked.size)

        picked.take(picked.size - tracked.size).forEach {
            assertEquals(
                it.toString(),
                readAndWrite,
                CapturedItems.safTreeFlagsToRelease(it, isRead = true, isWrite = true, tracked),
            )
        }
        tracked.forEach {
            assertEquals(
                it.toString(),
                0,
                CapturedItems.safTreeFlagsToRelease(it, isRead = true, isWrite = true, tracked),
            )
        }
    }

    /** A directory the app still lists keeps its grant, whether it is the current one or a past one. */
    @Test
    fun trackedTreesKeepTheirGrants() {
        val session = session()
        pickStorageLocation(session, tree("previous"))
        pickStorageLocation(session, tree("current"))

        val tracked = runBlocking { session.trackedSafTrees() }
        assertEquals(listOf(tree("current"), tree("previous")), tracked)
        assertEquals(
            0,
            CapturedItems.safTreeFlagsToRelease(
                tree("current"),
                isRead = true,
                isWrite = true,
                tracked,
            ),
        )
        assertEquals(
            0,
            CapturedItems.safTreeFlagsToRelease(
                tree("previous"),
                isRead = true,
                isWrite = true,
                tracked,
            ),
        )
    }

    /** Persisted grants that are not trees belong to some other feature, not to storage locations. */
    @Test
    fun grantsThatAreNotTreesAreLeftAlone() {
        val tracked = emptyList<Uri>()

        val document = DocumentsContract.buildDocumentUri(authority, "primary:DCIM/IMG_1.jpg")
        assertEquals(
            0,
            CapturedItems.safTreeFlagsToRelease(document, isRead = true, isWrite = true, tracked),
        )

        val mediaStore = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        assertEquals(
            0,
            CapturedItems.safTreeFlagsToRelease(mediaStore, isRead = true, isWrite = true, tracked),
        )
    }

    /** The release covers exactly the modes the grant holds, and a grant holding none is skipped. */
    @Test
    fun onlyTheModesTheGrantHoldsAreReleased() {
        val untracked = tree("untracked")
        val tracked = emptyList<Uri>()

        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            CapturedItems.safTreeFlagsToRelease(untracked, isRead = true, isWrite = false, tracked),
        )
        assertEquals(
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            CapturedItems.safTreeFlagsToRelease(untracked, isRead = false, isWrite = true, tracked),
        )
        assertEquals(
            readAndWrite,
            CapturedItems.safTreeFlagsToRelease(untracked, isRead = true, isWrite = true, tracked),
        )
        assertEquals(
            0,
            CapturedItems.safTreeFlagsToRelease(
                untracked,
                isRead = false,
                isWrite = false,
                tracked,
            ),
        )
    }
}
