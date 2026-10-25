package app.grapheneos.camera.data.media.repository

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.media.mapper.CapturedItemNameMapperImpl
import app.grapheneos.camera.data.media.mapper.SafTreeReleaseFlagsMapperImpl
import app.grapheneos.camera.data.media.mapper.StoredCapturedItemMapperImpl
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.store.MediaPrefs
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.media.store.StoredCapturedItem
import app.grapheneos.camera.data.media.store.mediaPrefsSerializer
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    @Test
    fun lastCapturedItem_afterRestart_returnsTheStoredItem() {
        runTest {
            repository().saveLastCapturedItem(item())

            val reloaded = repository().lastCapturedItem.first()

            assertEquals(item(), reloaded)
            assertEquals(CapturedItemType.IMAGE, reloaded?.type)
        }
    }

    @Test
    fun lastCapturedItem_freshInstall_returnsNull() {
        runTest {
            assertNull(repository().lastCapturedItem.first())
        }
    }

    @Test
    fun lockscreenSession_captures_reachTheCapturesFileAndNothingElse() {
        runTest {
            val file = File(temporaryFolder.root, "media_prefs.json")
            val durableMedia = DataStoreFactory.create(
                serializer = mediaPrefsSerializer,
                scope = fileScope,
            ) {
                file
            }
            val session = repository(session = lockscreenPrefs(), media = durableMedia)

            session.saveLastCapturedItem(item())
            session.setStorageLocation(treeUri("treeA").toString())

            val onDisk = mediaPrefsSerializer.readFrom(file.inputStream())

            assertEquals(item(), session.lastCapturedItem.first())
            assertEquals(
                StoredCapturedItem(
                    type = StoredCapturedItem.TYPE_IMAGE,
                    dateString = DATE_STRING,
                    uri = item().uri.toString(),
                ),
                onDisk.lastCapturedItem,
            )
            assertEquals(StoragePrefs(), stored())
        }
    }

    @Test
    fun releaseUntrackedSafTrees_lockscreenSession_touchesNoPersistedGrant() {
        runTest {
            val lockscreenResolver = mockk<ContentResolver>()
            val repository = LockscreenCapturedItemRepository(
                repository(
                    session = lockscreenPrefs(),
                    contentResolver = lockscreenResolver,
                ),
            )

            repository.releaseUntrackedSafTrees()

            verify { lockscreenResolver wasNot Called }
        }
    }

    @Test
    fun storageLocation_writtenThroughAnotherInstance_isVisible() {
        runTest {
            val reader = repository()
            val writer = repository()
            val location = treeUri("treeA").toString()

            writer.setStorageLocation(location)

            assertEquals(location, reader.storageLocation.first())
            assertEquals(listOf(treeUri("treeA")), reader.trackedSafTrees())
        }
    }

    @Test
    fun setStorageLocation_beyondTheCap_keepsTheMostRecentPreviousTreesOnly() {
        runTest {
            val repository = repository()
            val tracked = CapturedItemRepositoryImpl.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES

            (0..tracked).forEach { index ->
                repository.setStorageLocation(treeUri("tree$index").toString())
            }

            assertEquals(
                (tracked - 1 downTo 0).map { treeUri("tree$it") },
                previousSafTrees(),
            )
        }
    }

    @Test
    fun setStorageLocation_sameTree_keepsThePreviousTreesUnchanged() {
        runTest {
            val current = treeUri("current").toString()
            val previous = listOf(treeUri("treeA").toString(), treeUri("treeB").toString())
            storagePrefs.updateData {
                StoragePrefs(storageLocation = current, previousSafTrees = previous)
            }

            repository().setStorageLocation(current)

            assertEquals(previous, stored().previousSafTrees)
        }
    }

    @Test
    fun migrateStoredCaptures_legacyUris_becomeTrackedTreesOnce() {
        runTest {
            writeLegacyMediaUris(listOf("treeA", "treeB"))

            repository().migrateStoredCaptures()

            assertNull(stored().legacyMediaUris)
            assertEquals(
                listOf(treeUri("treeA"), treeUri("treeB")),
                previousSafTrees(),
            )

            repository().setStorageLocation(treeUri("treeC").toString())
            repository().setStorageLocation(treeUri("treeD").toString())
            repository().migrateStoredCaptures()

            assertEquals(
                listOf(treeUri("treeC"), treeUri("treeA"), treeUri("treeB")),
                previousSafTrees(),
            )
        }
    }

    @Test
    fun migrateStoredCaptures_currentStorageLocation_isNotTrackedAsAPreviousOne() {
        runTest {
            val location = treeUri("treeA").toString()
            storagePrefs.updateData { it.copy(storageLocation = location) }
            writeLegacyMediaUris(listOf("treeA"))

            repository().migrateStoredCaptures()

            assertEquals(emptyList<Uri>(), previousSafTrees())
        }
    }

    @Test
    fun migrateStoredCaptures_moreLegacyTreesThanTheCap_keepsThemAll() {
        runTest {
            val cap = CapturedItemRepositoryImpl.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES
            val trees = (0..cap).map { "tree$it" }
            writeLegacyMediaUris(trees)

            repository().migrateStoredCaptures()

            assertEquals(trees.map { treeUri(it) }, previousSafTrees())

            repository().setStorageLocation(treeUri("picked").toString())
            repository().setStorageLocation(treeUri("current").toString())

            assertEquals(
                listOf(treeUri("picked")) + trees.take(cap - 1).map { treeUri(it) },
                previousSafTrees(),
            )
        }
    }

    @Test
    fun migrateStoredCaptures_legacyUris_reportTheMostRecentAsTheLastCapturedItem() {
        runTest {
            documents()
            writeLegacyMediaUris(listOf("treeA"))

            val reported = repository().migrateStoredCaptures()

            assertEquals(
                CapturedItem(
                    type = CapturedItemType.IMAGE,
                    dateString = DATE_STRING,
                    uri = documentUri("treeA"),
                ),
                reported,
            )
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [IMAGE_DECODER_SDK])
    fun loadPreview_photoBeyondTheLimit_isScaledDownToIt() {
        runTest {
            documents().file = jpeg(width = 9000, height = 10)

            val preview = requireNotNull(repository().loadPreview(photo()))

            assertEquals(4500, preview.width)
            assertEquals(5, preview.height)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [IMAGE_DECODER_SDK])
    fun loadPreview_photoWithinTheLimit_keepsItsSize() {
        runTest {
            documents().file = jpeg(width = 40, height = 20)

            val preview = requireNotNull(repository().loadPreview(photo()))

            assertEquals(40, preview.width)
            assertEquals(20, preview.height)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [IMAGE_DECODER_SDK])
    fun loadPreview_truncatedPhoto_isNull() {
        runTest {
            val whole = jpeg(width = 40, height = 20)
            documents().file = temporaryFolder.newFile().apply {
                writeBytes(whole.readBytes().copyOf(whole.length().toInt() / 2))
            }

            assertNull(repository().loadPreview(photo()))
        }
    }

    @Test
    fun loadPreview_unreadablePhoto_isNull() {
        runTest {
            documents()

            assertNull(repository().loadPreview(photo()))
        }
    }

    private fun TestScope.repository(
        session: DataStore<StoragePrefs> = storagePrefs,
        media: DataStore<MediaPrefs> = mediaPrefs,
        contentResolver: ContentResolver = context.contentResolver,
    ): CapturedItemRepositoryImpl {
        return CapturedItemRepositoryImpl(
            storagePrefs = session,
            mediaPrefs = media,
            capturedItemNameMapper = CapturedItemNameMapperImpl(),
            storedCapturedItemMapper = StoredCapturedItemMapperImpl(),
            safTreeReleaseFlagsMapper = SafTreeReleaseFlagsMapperImpl(),
            contentResolver = contentResolver,
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            videoFrameDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    private suspend fun stored(): StoragePrefs {
        return storagePrefs.data.first()
    }

    private suspend fun previousSafTrees(): List<Uri> {
        return stored().previousSafTrees.map { Uri.parse(it) }
    }

    private suspend fun lockscreenPrefs(): DataStore<StoragePrefs> {
        return InMemoryDataStore(stored())
    }

    private fun item(): CapturedItem {
        return CapturedItem(
            type = CapturedItemType.IMAGE,
            dateString = DATE_STRING,
            uri = Uri.parse("content://media/external/images/media/1"),
        )
    }

    private fun documents(): FakeDocumentsProvider {
        return Robolectric
            .buildContentProvider(FakeDocumentsProvider::class.java)
            .create(AUTHORITY)
            .get()
    }

    private fun jpeg(width: Int, height: Int): File {
        val file = temporaryFolder.newFile()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { stream ->
            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                JPEG_QUALITY,
                stream,
            )
        }

        return file
    }

    private fun photo(): CapturedItem {
        return CapturedItem(
            type = CapturedItemType.IMAGE,
            dateString = DATE_STRING,
            uri = documentUri("tree"),
        )
    }

    private fun documentUri(treeId: String): Uri {
        val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, treeId)
        return DocumentsContract.buildDocumentUriUsingTree(tree, "$treeId/photo.jpg")
    }

    private fun treeUri(treeId: String): Uri {
        return DocumentsContract.buildTreeDocumentUri(AUTHORITY, treeId)
    }

    private suspend fun writeLegacyMediaUris(treeIds: List<String>) {
        val joined = treeIds.joinToString(separator = LEGACY_MEDIA_URI_SEPARATOR) {
            documentUri(it).toString()
        }

        storagePrefs.updateData { it.copy(legacyMediaUris = joined) }
    }

    private class FakeDocumentsProvider : ContentProvider() {

        var file: File? = null

        override fun onCreate(): Boolean {
            return true
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val target = file ?: throw FileNotFoundException("no file for $uri")

            return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val cursor = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            cursor.addRow(arrayOf("${CapturedItemType.IMAGE.namePrefix}$DATE_STRING.jpg"))
            return cursor
        }

        override fun getType(uri: Uri): String {
            return "image/jpeg"
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
        const val JPEG_QUALITY = 90
        const val LEGACY_MEDIA_URI_SEPARATOR = ";"

        // Robolectric 4.17 decodes from a file descriptor only up to API 34; on 35 ImageDecoder
        // throws "Only supported on Android" and loadPreview reads every photo as unreadable
        const val IMAGE_DECODER_SDK = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}
