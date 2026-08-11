package app.grapheneos.camera

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.media.store.CapturedItemStore
import app.grapheneos.camera.data.media.store.CapturedItemStoreImpl
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.settings.repository.SettingsRepositoryImpl
import app.grapheneos.camera.util.EphemeralSharedPrefs
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A directory the user picks to save captures in is granted to the app persistably, and the grant
 * used to outlive the app's own record of it: once enough other directories had been picked to push
 * one off the tracked list, nothing released it and the app kept indefinite read/write access to a
 * folder it no longer had any use for.
 */
@RunWith(AndroidJUnit4::class)
class SafTreeGrantsRegressionTest {

    private val authority = "com.android.externalstorage.documents"

    private val readAndWrite =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    private fun tree(name: String): Uri {
        return DocumentsContract.buildTreeDocumentUri(authority, "primary:$name")
    }

    private val targetSdk = InstrumentationRegistry
        .getInstrumentation()
        .targetContext
        .applicationInfo
        .targetSdkVersion

    private fun session(): Session {
        return Session(targetSdk)
    }

    /** What CamConfig.storageLocation records when the user picks a directory. */
    private fun pickStorageLocation(session: Session, treeUri: Uri) {
        session.store.currentSafTree()?.let {
            session.store.trackSafTree(it)
        }
        runBlocking {
            session.settings.setStorageLocation(treeUri.toString()).collect()
        }
    }

    /** The regression itself: the directory pushed off the tracked list is the one to release. */
    @Test
    fun theTreeThatFallsOffTheTrackedListIsReleased() {
        val session = session()
        val picked = (0..CapturedItems.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES + 1)
            .map { tree("dir$it") }
        picked.forEach { pickStorageLocation(session, it) }

        val tracked = session.store.safTrees()
        assertEquals(CapturedItems.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES + 1, tracked.size)

        picked.take(picked.size - tracked.size).forEach {
            assertEquals(
                it.toString(),
                readAndWrite,
                CapturedItems.safTreeFlagsToRelease(it, true, true, tracked),
            )
        }
        tracked.forEach {
            assertEquals(
                it.toString(), 0, CapturedItems.safTreeFlagsToRelease(it, true, true, tracked)
            )
        }
    }

    /** A directory the app still lists keeps its grant, whether it is the current one or a past one. */
    @Test
    fun trackedTreesKeepTheirGrants() {
        val session = session()
        pickStorageLocation(session, tree("previous"))
        pickStorageLocation(session, tree("current"))

        val tracked = session.store.safTrees()
        assertEquals(listOf(tree("current"), tree("previous")), tracked)
        assertEquals(0, CapturedItems.safTreeFlagsToRelease(tree("current"), true, true, tracked))
        assertEquals(0, CapturedItems.safTreeFlagsToRelease(tree("previous"), true, true, tracked))
    }

    /** Persisted grants that are not trees belong to some other feature, not to storage locations. */
    @Test
    fun grantsThatAreNotTreesAreLeftAlone() {
        val tracked = emptyList<Uri>()

        val document = DocumentsContract.buildDocumentUri(authority, "primary:DCIM/IMG_1.jpg")
        assertEquals(0, CapturedItems.safTreeFlagsToRelease(document, true, true, tracked))

        val mediaStore = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        assertEquals(0, CapturedItems.safTreeFlagsToRelease(mediaStore, true, true, tracked))
    }

    @Test
    fun onlyTheModesTheGrantHoldsAreReleased() {
        val untracked = tree("untracked")
        val tracked = emptyList<Uri>()

        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            CapturedItems.safTreeFlagsToRelease(untracked, true, false, tracked),
        )
        assertEquals(
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            CapturedItems.safTreeFlagsToRelease(untracked, false, true, tracked),
        )
        assertEquals(
            readAndWrite, CapturedItems.safTreeFlagsToRelease(untracked, true, true, tracked)
        )
        assertEquals(0, CapturedItems.safTreeFlagsToRelease(untracked, false, false, tracked))
    }

    /**
     * The store and the settings share one commons, because the storage location the user picks is
     * written by the settings and read back by the store. The captures file stays separate, as it is
     * in the app: aliasing the two here would let a confusion between them pass unnoticed.
     */
    private class Session(
        targetSdk: Int,
    ) {

        private val commons = EphemeralSharedPrefs(targetSdk)

        private val media = EphemeralSharedPrefs(targetSdk)

        val store: CapturedItemStore = CapturedItemStoreImpl(commons = commons, media = media)

        val settings: SettingsRepository = SettingsRepositoryImpl(
            commons = commons,
            modePreferences = CameraMode.entries.associateWith {
                lazy { EphemeralSharedPrefs(targetSdk) }
            },
        )
    }
}
