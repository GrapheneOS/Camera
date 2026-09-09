package app.grapheneos.camera.data.media

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.IMAGE_NAME_PREFIX
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.media.repository.CapturedItemRepositoryImpl
import app.grapheneos.camera.data.media.repository.LockscreenCapturedItemRepository
import app.grapheneos.camera.data.media.store.MediaPrefs
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.media.store.StoredCapturedItem
import app.grapheneos.camera.data.media.store.mediaPrefsSerializer
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CapturedItemRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val fileExecutor = Executors.newSingleThreadExecutor()

    private val fileScope = CoroutineScope(SupervisorJob() + fileExecutor.asCoroutineDispatcher())

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val storagePrefs: DataStore<StoragePrefs> = InMemoryDataStore(StoragePrefs())

    private val mediaPrefs: DataStore<MediaPrefs> = InMemoryDataStore(MediaPrefs())

    @After
    fun stopWriting() {
        fileScope.cancel()
        fileExecutor.shutdownNow()
    }

    private fun repository(
        session: DataStore<StoragePrefs> = storagePrefs,
        media: DataStore<MediaPrefs> = mediaPrefs,
        context: Context = this.context,
    ): CapturedItemRepositoryImpl {
        return CapturedItemRepositoryImpl(
            storagePrefs = session,
            mediaPrefs = media,
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    private fun stored(): StoragePrefs {
        return runBlocking { storagePrefs.data.first() }
    }

    private fun previousSafTrees(): List<Uri> {
        return stored().previousSafTrees.map { Uri.parse(it) }
    }

    private fun lockscreenPrefs(): DataStore<StoragePrefs> {
        return InMemoryDataStore(stored())
    }

    private fun item(dateString: String): CapturedItem {
        return CapturedItem(
            type = ITEM_TYPE_IMAGE,
            dateString = dateString,
            uri = Uri.parse("content://media/external/images/media/1"),
        )
    }

    private fun documentUri(treeId: String): Uri {
        val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, treeId)
        return DocumentsContract.buildDocumentUriUsingTree(tree, "$treeId/photo.jpg")
    }

    private fun treeUri(treeId: String): Uri {
        return DocumentsContract.buildTreeDocumentUri(AUTHORITY, treeId)
    }

    @Test
    fun lastCapturedItem_afterRestart_returnsTheStoredItem() {
        runBlocking { repository().saveLastCapturedItem(item(DATE_STRING)) }

        val reloaded = runBlocking { repository().lastCapturedItem.first() }

        assertEquals(item(DATE_STRING), reloaded)
        assertEquals(ITEM_TYPE_IMAGE, reloaded?.type)
    }

    @Test
    fun lastCapturedItem_freshInstall_returnsNull() {
        assertNull(runBlocking { repository().lastCapturedItem.first() })
    }

    @Test
    fun lockscreenSession_captures_reachTheCapturesFileAndNothingElse() {
        val file = File(temporaryFolder.root, "media_prefs.json")
        val durableMedia = DataStoreFactory.create(
            serializer = mediaPrefsSerializer,
            scope = fileScope,
        ) {
            file
        }
        val session = repository(session = lockscreenPrefs(), media = durableMedia)

        runBlocking {
            session.saveLastCapturedItem(item(DATE_STRING))
            session.setStorageLocation(treeUri("treeA").toString())
        }

        val onDisk = runBlocking { mediaPrefsSerializer.readFrom(file.inputStream()) }

        assertEquals(item(DATE_STRING), runBlocking { session.lastCapturedItem.first() })
        assertEquals(
            StoredCapturedItem(
                type = ITEM_TYPE_IMAGE,
                dateString = DATE_STRING,
                uri = item(DATE_STRING).uri.toString(),
            ),
            onDisk.lastCapturedItem,
        )
        assertEquals(StoragePrefs(), stored())
    }

    @Test
    fun releaseUntrackedSafTrees_lockscreenSession_touchesNoPersistedGrant() {
        val repository = LockscreenCapturedItemRepository(
            repository(
                session = lockscreenPrefs(),
                context = NoContentResolverContext(context),
            ),
        )

        runBlocking { repository.releaseUntrackedSafTrees() }
    }

    @Test
    fun storageLocation_writtenThroughAnotherInstance_isVisible() {
        val reader = repository()
        val writer = repository()
        val location = treeUri("treeA").toString()

        runBlocking { writer.setStorageLocation(location) }

        assertEquals(location, runBlocking { reader.storageLocation.first() })
        assertEquals(listOf(treeUri("treeA")), runBlocking { reader.trackedSafTrees() })
    }

    @Test
    fun setStorageLocation_beyondTheCap_keepsTheMostRecentPreviousTreesOnly() {
        val repository = repository()
        val tracked = CapturedItems.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES

        (0..tracked).forEach { index ->
            runBlocking { repository.setStorageLocation(treeUri("tree$index").toString()) }
        }

        assertEquals(
            (tracked - 1 downTo 0).map { treeUri("tree$it") },
            previousSafTrees(),
        )
    }

    @Test
    fun setStorageLocation_sameTree_keepsThePreviousTreesUnchanged() {
        val current = treeUri("current").toString()
        val previous = listOf(treeUri("treeA").toString(), treeUri("treeB").toString())
        runBlocking {
            storagePrefs.updateData {
                StoragePrefs(storageLocation = current, previousSafTrees = previous)
            }
        }

        runBlocking { repository().setStorageLocation(current) }

        assertEquals(previous, stored().previousSafTrees)
    }

    @Test
    fun migrateStoredCaptures_legacyUris_becomeTrackedTreesOnce() {
        writeLegacyMediaUris(listOf("treeA", "treeB"))

        runBlocking { repository().migrateStoredCaptures() }

        assertNull(stored().legacyMediaUris)
        assertEquals(
            listOf(treeUri("treeA"), treeUri("treeB")),
            previousSafTrees(),
        )

        runBlocking {
            repository().setStorageLocation(treeUri("treeC").toString())
            repository().setStorageLocation(treeUri("treeD").toString())
            repository().migrateStoredCaptures()
        }

        assertEquals(
            listOf(treeUri("treeC"), treeUri("treeA"), treeUri("treeB")),
            previousSafTrees(),
        )
    }

    @Test
    fun migrateStoredCaptures_currentStorageLocation_isNotTrackedAsAPreviousOne() {
        val location = treeUri("treeA").toString()
        runBlocking { storagePrefs.updateData { it.copy(storageLocation = location) } }
        writeLegacyMediaUris(listOf("treeA"))

        runBlocking { repository().migrateStoredCaptures() }

        assertEquals(emptyList<Uri>(), previousSafTrees())
    }

    @Test
    fun migrateStoredCaptures_moreLegacyTreesThanTheCap_keepsThemAll() {
        val cap = CapturedItems.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES
        val trees = (0..cap).map { "tree$it" }
        writeLegacyMediaUris(trees)

        runBlocking { repository().migrateStoredCaptures() }

        assertEquals(trees.map { treeUri(it) }, previousSafTrees())

        runBlocking {
            repository().setStorageLocation(treeUri("picked").toString())
            repository().setStorageLocation(treeUri("current").toString())
        }

        assertEquals(
            listOf(treeUri("picked")) + trees.take(cap - 1).map { treeUri(it) },
            previousSafTrees(),
        )
    }

    @Test
    fun migrateStoredCaptures_legacyUris_reportTheMostRecentAsTheLastCapturedItem() {
        Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create(AUTHORITY)
        writeLegacyMediaUris(listOf("treeA"))

        val reported = runBlocking { repository().migrateStoredCaptures() }

        assertEquals(
            CapturedItem(
                type = ITEM_TYPE_IMAGE,
                dateString = DATE_STRING,
                uri = documentUri("treeA"),
            ),
            reported,
        )
    }

    private fun writeLegacyMediaUris(treeIds: List<String>) {
        val joined = treeIds.joinToString(separator = LEGACY_MEDIA_URI_SEPARATOR) {
            documentUri(it).toString()
        }

        runBlocking { storagePrefs.updateData { it.copy(legacyMediaUris = joined) } }
    }

    private class NoContentResolverContext(
        base: Context,
    ) : ContextWrapper(base) {

        override fun getContentResolver(): ContentResolver {
            throw AssertionError("a lockscreen session must not touch persisted grants")
        }
    }

    private class FakeDocumentsProvider : ContentProvider() {

        override fun onCreate(): Boolean {
            return true
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val cursor = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            cursor.addRow(arrayOf("$IMAGE_NAME_PREFIX$DATE_STRING.jpg"))
            return cursor
        }

        override fun getType(uri: Uri): String? {
            return null
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            throw UnsupportedOperationException()
        }

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            throw UnsupportedOperationException()
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            throw UnsupportedOperationException()
        }
    }

    private companion object {
        const val AUTHORITY = "com.example.documents"
        const val DATE_STRING = "20260724_153012_345"
        const val LEGACY_MEDIA_URI_SEPARATOR = ";"
    }
}
