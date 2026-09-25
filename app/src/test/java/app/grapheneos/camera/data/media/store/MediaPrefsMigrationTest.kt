package app.grapheneos.camera.data.media.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.data.media.store.MediaPrefs
import app.grapheneos.camera.data.media.store.MediaPrefsMigration
import app.grapheneos.camera.data.media.store.StoredCapturedItem
import app.grapheneos.camera.testutil.LEGACY_ITEM_DATE_STRING
import app.grapheneos.camera.testutil.LEGACY_ITEM_URI
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
class MediaPrefsMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val migration = MediaPrefsMigration(context)

    @Before
    fun clearLegacyFiles() {
        clearLegacyPreferences(context)
    }

    @Test
    fun shouldMigrate_freshInstall_isFalse() {
        runTest {
            assertFalse(migration.shouldMigrate(MediaPrefs()))
        }
    }

    @Test
    fun migrate_lastCapturedItemFromTheOldFiles_isCarriedOver() {
        runTest {
            writeLegacyPreferences(context)

            assertTrue("nothing to migrate", migration.shouldMigrate(MediaPrefs()))

            val migrated = migration.migrate(MediaPrefs())

            assertEquals(
                StoredCapturedItem(
                    type = ITEM_TYPE_IMAGE,
                    dateString = LEGACY_ITEM_DATE_STRING,
                    uri = LEGACY_ITEM_URI,
                ),
                migrated.lastCapturedItem,
            )
        }
    }

    @Test
    fun migrate_populatedDataStore_isKeptBeforeTheLegacyFile() {
        runTest {
            val current = StoredCapturedItem(
                type = ITEM_TYPE_IMAGE,
                dateString = CURRENT_DATE_STRING,
                uri = CURRENT_URI,
            )
            writeCapturedItem(
                preferences = legacyCommonsFile(context),
                dateString = COMMONS_DATE_STRING,
                uri = COMMONS_URI,
            )

            val migrated = migration.migrate(MediaPrefs(lastCapturedItem = current))

            assertEquals(current, migrated.lastCapturedItem)
        }
    }

    @Test
    fun migrate_anIncompleteLegacyRecord_leavesWhatIsStoredAlone() {
        runTest {
            legacyCommonsFile(context).edit(commit = true) {
                putInt("last_captured_item_type", ITEM_TYPE_IMAGE)
            }

            val migrated = migration.migrate(MediaPrefs())

            assertNull(migrated.lastCapturedItem)
        }
    }

    @Test
    fun shouldMigrate_afterTheItemHasBeenCarriedOver_isFalse() {
        runTest {
            writeLegacyPreferences(context)

            migration.migrate(MediaPrefs())
            migration.cleanUp()

            assertFalse(migration.shouldMigrate(MediaPrefs()))
        }
    }

    @Test
    fun shouldMigrate_onlyAnotherOwnersKeysArePresent_isFalse() {
        runTest {
            legacyCommonsFile(context).edit(commit = true) {
                putInt("photo_quality", SOME_PHOTO_QUALITY)
            }

            assertFalse(migration.shouldMigrate(MediaPrefs()))
        }
    }

    private fun writeCapturedItem(
        preferences: SharedPreferences,
        dateString: String,
        uri: String,
    ) {
        preferences.edit(commit = true) {
            putInt("last_captured_item_type", ITEM_TYPE_IMAGE)
            putString("last_captured_item_date_string", dateString)
            putString("last_captured_item_uri", uri)
        }
    }

    private companion object {
        const val SOME_PHOTO_QUALITY = 71
        const val COMMONS_DATE_STRING = "20260724_100000_000"
        const val COMMONS_URI = "content://media/external/images/media/2"
        const val CURRENT_DATE_STRING = "20260724_120000_000"
        const val CURRENT_URI = "content://media/external/images/media/4"
    }
}
