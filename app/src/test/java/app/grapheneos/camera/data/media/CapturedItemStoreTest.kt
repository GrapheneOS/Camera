package app.grapheneos.camera.data.media

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.IMAGE_NAME_PREFIX
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.data.core.store.STORAGE_LOCATION_KEY
import app.grapheneos.camera.data.core.store.commonPreferences
import app.grapheneos.camera.data.core.store.mediaPreferences
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepositoryImpl
import app.grapheneos.camera.data.media.repository.LockscreenCapturedItemRepository
import app.grapheneos.camera.data.media.store.CapturedItemStore
import app.grapheneos.camera.data.media.store.CapturedItemStoreImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * A lockscreen capture must still reach the owner's gallery while nothing else about the session
 * does, and the legacy uri migration runs against installs nobody can rebuild.
 */
@RunWith(RobolectricTestRunner::class)
class CapturedItemStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun persistentCommons(): SharedPreferences {
        return commonPreferences(context, ephemeral = false)
    }

    private fun persistentMedia(): SharedPreferences {
        return mediaPreferences(context)
    }

    private fun store(ephemeral: Boolean = false): CapturedItemStore {
        return CapturedItemStoreImpl(
            commons = commonPreferences(context, ephemeral = ephemeral),
            media = persistentMedia(),
        )
    }

    private fun repository(store: CapturedItemStore): CapturedItemRepository {
        return CapturedItemRepositoryImpl(store = store, context = context)
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

    @Before
    fun clearPersistentPrefs() {
        persistentCommons().edit().clear().commit()
        persistentMedia().edit().clear().commit()
    }

    @Test
    fun readLastCapturedItem_afterRestart_returnsTheStoredItem() {
        store().writeLastCapturedItem(item(DATE_STRING))

        val reloaded = store().readLastCapturedItem()

        assertEquals(item(DATE_STRING), reloaded)
        assertEquals(ITEM_TYPE_IMAGE, reloaded?.type)
    }

    @Test
    fun readLastCapturedItem_freshInstall_returnsNull() {
        assertNull(store().readLastCapturedItem())
    }

    @Test
    fun lockscreenSession_captures_reachTheCapturesFileAndNothingElse() {
        val session = store(ephemeral = true)

        session.writeLastCapturedItem(item(DATE_STRING))
        session.trackSafTree(treeUri("treeA"))

        assertEquals(item(DATE_STRING), session.readLastCapturedItem())
        assertEquals(item(DATE_STRING), store().readLastCapturedItem())
        assertEquals(emptyList<Uri>(), store().previousSafTrees())
    }

    /**
     * A release is durable and cannot be undone when the session ends, so the repository a lockscreen
     * session gets must not reach the ContentResolver at all — a Context that throws on any access
     * to it is the only way to assert "never" rather than "not with this fixture's data".
     */
    @Test
    fun releaseUntrackedSafTrees_lockscreenSession_touchesNoPersistedGrant() {
        val repository = LockscreenCapturedItemRepository(
            CapturedItemRepositoryImpl(
                store = store(ephemeral = true),
                context = NoContentResolverContext(context),
            ),
        )

        repository.releaseUntrackedSafTrees()
    }

    @Test
    fun migrateStoredCaptures_lastCapturedItemInCommons_movesItToTheCapturesFile() {
        writeLegacyLastCapturedItem(DATE_STRING)

        repository(store()).migrateStoredCaptures { }

        assertEquals(item(DATE_STRING), store().readLastCapturedItem())
        assertFalse(persistentCommons().contains(LEGACY_LAST_CAPTURED_ITEM_DATE_STRING))
    }

    @Test
    fun migrateStoredCaptures_capturesFileAlreadyPopulated_leavesItAlone() {
        store().writeLastCapturedItem(item(DATE_STRING))
        writeLegacyLastCapturedItem(OLDER_DATE_STRING)

        repository(store()).migrateStoredCaptures { }

        assertEquals(item(DATE_STRING), store().readLastCapturedItem())
    }

    @Test
    fun init_doesNotMigrateAnything() {
        writeLegacyLastCapturedItem(DATE_STRING)

        store()

        assertNull(persistentMedia().getString(LEGACY_LAST_CAPTURED_ITEM_DATE_STRING, null))
        assertEquals(
            DATE_STRING,
            persistentCommons().getString(LEGACY_LAST_CAPTURED_ITEM_DATE_STRING, null),
        )
    }

    @Test
    fun trackSafTree_beyondTheCap_keepsTheMostRecentOnly() {
        val store = store()
        val tracked = CapturedItems.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES

        (0..tracked).forEach { index ->
            store.trackSafTree(treeUri("tree$index"))
        }

        assertEquals(
            (tracked downTo 1).map { treeUri("tree$it") },
            store().previousSafTrees(),
        )
    }

    @Test
    fun migrateStoredCaptures_legacyUris_becomeTrackedTreesOnce() {
        val prefs = persistentCommons()
        val legacy = listOf("treeA", "treeB").joinToString(separator = ";") {
            documentUri(it).toString()
        }
        prefs.edit().putString(LEGACY_MEDIA_URIS, legacy).commit()

        repository(store()).migrateStoredCaptures { }

        assertFalse(prefs.contains(LEGACY_MEDIA_URIS))
        assertEquals(
            listOf(treeUri("treeA"), treeUri("treeB")),
            store().previousSafTrees(),
        )

        store().trackSafTree(treeUri("treeC"))
        repository(store()).migrateStoredCaptures { }

        assertEquals(
            listOf(treeUri("treeC"), treeUri("treeA"), treeUri("treeB")),
            store().previousSafTrees(),
        )
    }

    @Test
    fun migrateStoredCaptures_currentStorageLocation_isNotTrackedAsAPreviousOne() {
        val prefs = persistentCommons()
        prefs.edit()
            .putString(STORAGE_LOCATION_KEY, treeUri("treeA").toString())
            .putString(LEGACY_MEDIA_URIS, documentUri("treeA").toString())
            .commit()

        repository(store()).migrateStoredCaptures { }

        assertEquals(emptyList<Uri>(), store().previousSafTrees())
    }

    @Test
    fun migrateStoredCaptures_moreLegacyTreesThanTheCap_keepsThemAll() {
        val prefs = persistentCommons()
        val cap = CapturedItems.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES
        val trees = (0..cap).map { "tree$it" }
        prefs.edit()
            .putString(
                LEGACY_MEDIA_URIS,
                trees.joinToString(separator = ";") { documentUri(it).toString() },
            )
            .commit()

        repository(store()).migrateStoredCaptures { }

        assertEquals(trees.map { treeUri(it) }, store().previousSafTrees())

        store().trackSafTree(treeUri("picked"))

        assertEquals(
            listOf(treeUri("picked")) + trees.take(cap - 1).map { treeUri(it) },
            store().previousSafTrees(),
        )
    }

    @Test
    fun migrateStoredCaptures_legacyUris_reportTheMostRecentAsTheLastCapturedItem() {
        Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create(AUTHORITY)
        val prefs = persistentCommons()
        prefs.edit().putString(LEGACY_MEDIA_URIS, documentUri("treeA").toString()).commit()

        var reported: CapturedItem? = null
        repository(store()).migrateStoredCaptures { reported = it }

        assertEquals(
            CapturedItem(
                type = ITEM_TYPE_IMAGE,
                dateString = DATE_STRING,
                uri = documentUri("treeA"),
            ),
            reported,
        )
    }

    private fun writeLegacyLastCapturedItem(dateString: String) {
        val stored = item(dateString)

        persistentCommons().edit()
            .putInt(LEGACY_LAST_CAPTURED_ITEM_TYPE, stored.type)
            .putString(LEGACY_LAST_CAPTURED_ITEM_DATE_STRING, stored.dateString)
            .putString(LEGACY_LAST_CAPTURED_ITEM_URI, stored.uri.toString())
            .commit()
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

    companion object {
        private const val AUTHORITY = "com.example.documents"
        private const val DATE_STRING = "20260724_153012_345"
        private const val OLDER_DATE_STRING = "20250101_090000_000"
        private const val LEGACY_MEDIA_URIS = "media_uri_s"
        private const val LEGACY_LAST_CAPTURED_ITEM_TYPE = "last_captured_item_type"
        private const val LEGACY_LAST_CAPTURED_ITEM_DATE_STRING = "last_captured_item_date_string"
        private const val LEGACY_LAST_CAPTURED_ITEM_URI = "last_captured_item_uri"
    }
}
