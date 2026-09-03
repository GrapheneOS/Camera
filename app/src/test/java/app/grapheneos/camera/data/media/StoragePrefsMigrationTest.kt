package app.grapheneos.camera.data.media

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.data.core.LEGACY_CAPTURE_KEY_NAMES
import app.grapheneos.camera.data.core.LEGACY_STORAGE_KEY_NAMES
import app.grapheneos.camera.data.core.clearLegacyPreferences
import app.grapheneos.camera.data.core.legacyCommonsFile
import app.grapheneos.camera.data.core.writeLegacyPreferences
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.media.store.StoragePrefsMigration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoragePrefsMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val migration = StoragePrefsMigration(context)

    @Before
    fun clearLegacyFiles() {
        clearLegacyPreferences(context)
    }

    @Test
    fun shouldMigrate_freshInstall_isFalse() {
        assertFalse(runBlocking { migration.shouldMigrate(StoragePrefs()) })
    }

    @Test
    fun migrate_whereCapturesAreSaved_isCarriedOverVerbatim() {
        val trees = listOf("content://tree/a", "content://tree/b")
        legacyCommonsFile(context).edit(commit = true) {
            putString(STORAGE_LOCATION, CURRENT_TREE)
            putString(
                PREVIOUS_SAF_TREES,
                trees.joinToString(separator = CapturedItems.SAF_TREE_SEPARATOR),
            )
            putString(LEGACY_MEDIA_URIS, LEGACY_URIS)
        }

        val migrated = runBlocking {
            assertTrue("nothing to migrate", migration.shouldMigrate(StoragePrefs()))
            migration.migrate(StoragePrefs())
        }

        assertEquals(CURRENT_TREE, migrated.storageLocation)
        assertEquals(trees, migrated.previousSafTrees)
        assertEquals(LEGACY_URIS, migrated.legacyMediaUris)
    }

    @Test
    fun migrate_aDirectoryWasNeverPicked_carriesOverNothing() {
        legacyCommonsFile(context).edit(commit = true) { putInt(PHOTO_QUALITY, SOME_PHOTO_QUALITY) }

        val migrated = runBlocking { migration.migrate(StoragePrefs()) }

        assertNull(migrated.storageLocation)
        assertEquals(emptyList<String>(), migrated.previousSafTrees)
        assertNull(migrated.legacyMediaUris)
    }

    @Test
    fun migrate_onlyPresentLegacyKeys_replaceCurrentData() {
        val current = StoragePrefs(
            storageLocation = "content://tree/new",
            previousSafTrees = listOf("content://tree/kept"),
            legacyMediaUris = "content://media/kept",
        )
        legacyCommonsFile(context).edit(commit = true) { putString(STORAGE_LOCATION, CURRENT_TREE) }

        val migrated = runBlocking { migration.migrate(current) }

        assertEquals(CURRENT_TREE, migrated.storageLocation)
        assertEquals(current.previousSafTrees, migrated.previousSafTrees)
        assertEquals(current.legacyMediaUris, migrated.legacyMediaUris)
    }

    @Test
    fun shouldMigrate_afterTheTreesHaveBeenCarriedOver_isFalse() {
        writeLegacyPreferences(context)

        runBlocking {
            migration.migrate(StoragePrefs())
            migration.cleanUp()
        }

        assertFalse(runBlocking { migration.shouldMigrate(StoragePrefs()) })
    }

    @Test
    fun shouldMigrate_onlyAnotherOwnersKeysArePresent_isFalse() {
        legacyCommonsFile(context).edit(commit = true) { putInt(PHOTO_QUALITY, SOME_PHOTO_QUALITY) }

        assertFalse(runBlocking { migration.shouldMigrate(StoragePrefs()) })
    }

    @Test
    fun cleanUp_removesOnlyItsOwnKeys() {
        writeLegacyPreferences(context)

        runBlocking {
            migration.migrate(StoragePrefs())
            migration.cleanUp()
        }

        val remaining = legacyCommonsFile(context).all.keys

        assertTrue(remaining.none { it in LEGACY_STORAGE_KEY_NAMES })
        assertTrue(remaining.containsAll(LEGACY_CAPTURE_KEY_NAMES))
        assertTrue(remaining.contains(PHOTO_QUALITY))
    }

    private companion object {
        const val STORAGE_LOCATION = "storage_location"
        const val PREVIOUS_SAF_TREES = "previous_saf_trees"
        const val LEGACY_MEDIA_URIS = "media_uri_s"
        const val PHOTO_QUALITY = "photo_quality"

        const val CURRENT_TREE = "content://tree/current"
        const val LEGACY_URIS = "content://tree/a/document/photo.jpg"
        const val SOME_PHOTO_QUALITY = 71
    }
}
