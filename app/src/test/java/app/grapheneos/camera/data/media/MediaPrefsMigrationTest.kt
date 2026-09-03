package app.grapheneos.camera.data.media

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.data.core.LEGACY_CAPTURE_KEY_NAMES
import app.grapheneos.camera.data.core.LEGACY_ITEM_DATE_STRING
import app.grapheneos.camera.data.core.LEGACY_ITEM_URI
import app.grapheneos.camera.data.core.clearLegacyPreferences
import app.grapheneos.camera.data.core.legacyCommonsFile
import app.grapheneos.camera.data.core.legacyMediaFile
import app.grapheneos.camera.data.core.legacyMediaFileExists
import app.grapheneos.camera.data.core.writeLegacyPreferences
import app.grapheneos.camera.data.media.store.MediaPrefs
import app.grapheneos.camera.data.media.store.MediaPrefsMigration
import app.grapheneos.camera.data.media.store.StoredCapturedItem
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
class MediaPrefsMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val migration = MediaPrefsMigration(context)

    @Before
    fun clearLegacyFiles() {
        clearLegacyPreferences(context)
    }

    @Test
    fun shouldMigrate_freshInstall_isFalse() {
        assertFalse(runBlocking { migration.shouldMigrate(MediaPrefs()) })
    }

    @Test
    fun migrate_lastCapturedItemFromTheOldFiles_isCarriedOver() {
        writeLegacyPreferences(context)

        val migrated = runBlocking {
            assertTrue("nothing to migrate", migration.shouldMigrate(MediaPrefs()))
            migration.migrate(MediaPrefs())
        }

        assertEquals(
            StoredCapturedItem(
                type = ITEM_TYPE_IMAGE,
                dateString = LEGACY_ITEM_DATE_STRING,
                uri = LEGACY_ITEM_URI,
            ),
            migrated.lastCapturedItem,
        )
    }

    @Test
    fun migrate_lastCapturedItemFromTheMediaFile_isCarriedOverBeforeTheOlderFile() {
        writeCapturedItem(
            preferences = legacyCommonsFile(context),
            dateString = COMMONS_DATE_STRING,
            uri = COMMONS_URI,
        )
        writeCapturedItem(
            preferences = legacyMediaFile(context),
            dateString = MEDIA_DATE_STRING,
            uri = MEDIA_URI,
        )

        val migrated = runBlocking { migration.migrate(MediaPrefs()) }

        assertEquals(MEDIA_DATE_STRING, migrated.lastCapturedItem?.dateString)
        assertEquals(MEDIA_URI, migrated.lastCapturedItem?.uri)
    }

    @Test
    fun migrate_populatedDataStore_isKeptBeforeEitherLegacyFile() {
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
        writeCapturedItem(
            preferences = legacyMediaFile(context),
            dateString = MEDIA_DATE_STRING,
            uri = MEDIA_URI,
        )

        assertEquals(
            current,
            runBlocking {
                migration.migrate(MediaPrefs(lastCapturedItem = current))
            }.lastCapturedItem,
        )
    }

    @Test
    fun migrate_anIncompleteLegacyRecord_leavesWhatIsStoredAlone() {
        legacyCommonsFile(context).edit(commit = true) {
            putInt("last_captured_item_type", ITEM_TYPE_IMAGE)
        }

        val migrated = runBlocking { migration.migrate(MediaPrefs()) }

        assertNull(migrated.lastCapturedItem)
    }

    @Test
    fun shouldMigrate_afterTheItemHasBeenCarriedOver_isFalse() {
        writeLegacyPreferences(context)

        runBlocking {
            migration.migrate(MediaPrefs())
            migration.cleanUp()
        }

        assertFalse(runBlocking { migration.shouldMigrate(MediaPrefs()) })
    }

    @Test
    fun shouldMigrate_onlyAnotherOwnersKeysArePresent_isFalse() {
        legacyCommonsFile(context).edit(commit = true) {
            putInt("photo_quality", SOME_PHOTO_QUALITY)
        }

        assertFalse(runBlocking { migration.shouldMigrate(MediaPrefs()) })
    }

    @Test
    fun cleanUp_removesOnlyItsOwnKeys() {
        writeLegacyPreferences(context)
        writeCapturedItem(
            preferences = legacyMediaFile(context),
            dateString = MEDIA_DATE_STRING,
            uri = MEDIA_URI,
        )

        runBlocking {
            migration.migrate(MediaPrefs())
            migration.cleanUp()
        }

        val remaining = legacyCommonsFile(context).all.keys

        assertTrue(remaining.none { it in LEGACY_CAPTURE_KEY_NAMES })
        assertTrue(remaining.contains("photo_quality"))
        assertFalse(legacyMediaFileExists(context))
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
        const val MEDIA_DATE_STRING = "20260724_110000_000"
        const val MEDIA_URI = "content://media/external/images/media/3"
        const val CURRENT_DATE_STRING = "20260724_120000_000"
        const val CURRENT_URI = "content://media/external/images/media/4"
    }
}
