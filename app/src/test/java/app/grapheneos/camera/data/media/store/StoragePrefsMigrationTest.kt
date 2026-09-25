package app.grapheneos.camera.data.media.store

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.media.store.StoragePrefsMigration
import app.grapheneos.camera.testutil.clearLegacyPreferences
import app.grapheneos.camera.testutil.legacyCommonsFile
import app.grapheneos.camera.testutil.writeLegacyPreferences
import kotlinx.coroutines.test.runTest
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
        runTest {
            assertFalse(migration.shouldMigrate(StoragePrefs()))
        }
    }

    @Test
    fun migrate_whereCapturesAreSaved_isCarriedOverVerbatim() {
        runTest {
            val trees = listOf("content://tree/a", "content://tree/b")
            legacyCommonsFile(context).edit(commit = true) {
                putString(STORAGE_LOCATION, CURRENT_TREE)
                putString(
                    PREVIOUS_SAF_TREES,
                    trees.joinToString(separator = CapturedItems.SAF_TREE_SEPARATOR),
                )
                putString(LEGACY_MEDIA_URIS, LEGACY_URIS)
            }

            assertTrue("nothing to migrate", migration.shouldMigrate(StoragePrefs()))

            val migrated = migration.migrate(StoragePrefs())

            assertEquals(CURRENT_TREE, migrated.storageLocation)
            assertEquals(trees, migrated.previousSafTrees)
            assertEquals(LEGACY_URIS, migrated.legacyMediaUris)
        }
    }

    @Test
    fun migrate_aDirectoryWasNeverPicked_carriesOverNothing() {
        runTest {
            legacyCommonsFile(context).edit(commit = true) {
                putInt(PHOTO_QUALITY, SOME_PHOTO_QUALITY)
            }

            val migrated = migration.migrate(StoragePrefs())

            assertNull(migrated.storageLocation)
            assertEquals(emptyList<String>(), migrated.previousSafTrees)
            assertNull(migrated.legacyMediaUris)
        }
    }

    @Test
    fun migrate_onlyPresentLegacyKeys_replaceCurrentData() {
        runTest {
            val current = StoragePrefs(
                storageLocation = "content://tree/new",
                previousSafTrees = listOf("content://tree/kept"),
                legacyMediaUris = "content://media/kept",
            )
            legacyCommonsFile(context).edit(commit = true) {
                putString(STORAGE_LOCATION, CURRENT_TREE)
            }

            val migrated = migration.migrate(current)

            assertEquals(CURRENT_TREE, migrated.storageLocation)
            assertEquals(current.previousSafTrees, migrated.previousSafTrees)
            assertEquals(current.legacyMediaUris, migrated.legacyMediaUris)
        }
    }

    @Test
    fun shouldMigrate_afterTheTreesHaveBeenCarriedOver_isFalse() {
        runTest {
            writeLegacyPreferences(context)

            migration.migrate(StoragePrefs())
            migration.cleanUp()

            assertFalse(migration.shouldMigrate(StoragePrefs()))
        }
    }

    @Test
    fun shouldMigrate_onlyAnotherOwnersKeysArePresent_isFalse() {
        runTest {
            legacyCommonsFile(context).edit(commit = true) {
                putInt(PHOTO_QUALITY, SOME_PHOTO_QUALITY)
            }

            assertFalse(migration.shouldMigrate(StoragePrefs()))
        }
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
