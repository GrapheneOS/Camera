package app.grapheneos.camera.data.core

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.media.store.MediaPrefs
import app.grapheneos.camera.data.media.store.MediaPrefsMigration
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.media.store.StoragePrefsMigration
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.SettingsPrefsMigration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegacyMigrationOwnershipTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val settings = SettingsPrefsMigration(context)

    private val storage = StoragePrefsMigration(context)

    private val media = MediaPrefsMigration(context)

    @Before
    fun clearLegacyFiles() {
        clearLegacyPreferences(context)
    }

    @Test
    fun migrations_inAnyOrder_produceTheSameResultAndLeaveNoLegacyFile() {
        val migrated = ORDERS.map { order -> migrateIn(order) }

        migrated.forEach { assertEquals(migrated.first(), it) }
    }

    @Test
    fun legacyKeyOwnership_isDisjointAndComplete() {
        val settingsOwned = keysRemovedBy { runBlocking { settings.cleanUp() } }
        val storageOwned = keysRemovedBy { runBlocking { storage.cleanUp() } }
        val mediaOwned = keysRemovedBy { runBlocking { media.cleanUp() } }

        assertEquals(emptySet<String>(), settingsOwned.intersect(storageOwned))
        assertEquals(emptySet<String>(), settingsOwned.intersect(mediaOwned))
        assertEquals(emptySet<String>(), storageOwned.intersect(mediaOwned))
        assertEquals(LEGACY_COMMON_ENTRIES.keys, settingsOwned + storageOwned + mediaOwned)
        assertEquals(LEGACY_STORAGE_KEY_NAMES, storageOwned)
        assertEquals(LEGACY_CAPTURE_KEY_NAMES, mediaOwned)
    }

    @Test
    fun cleanUp_lastOwnerToRun_deletesTheCommonsFile() {
        writeLegacyPreferences(context)

        runBlocking { media.cleanUp() }

        assertTrue("two owners have still to read", legacyCommonsFileExists(context))

        runBlocking { storage.cleanUp() }

        assertTrue("the settings have still to be read", legacyCommonsFileExists(context))

        runBlocking { settings.cleanUp() }

        assertFalse(legacyCommonsFileExists(context))
    }

    private fun migrateIn(order: List<Owner>): Migrated {
        clearLegacyPreferences(context)
        writeLegacyPreferences(context)

        val migrated = order.fold(Migrated()) { carried, owner -> migrate(owner, carried) }

        assertNoLegacyFileLeft(order.joinToString())

        return migrated
    }

    private fun migrate(owner: Owner, into: Migrated): Migrated {
        return when (owner) {
            Owner.SETTINGS -> into.copy(settings = carryOver(settings, SettingsPrefs()))
            Owner.STORAGE -> into.copy(storage = carryOver(storage, StoragePrefs()))
            Owner.MEDIA -> into.copy(media = carryOver(media, MediaPrefs()))
        }
    }

    private fun <T> carryOver(migration: DataMigration<T>, freshInstall: T): T {
        return runBlocking {
            assertTrue("nothing to migrate", migration.shouldMigrate(freshInstall))

            val migrated = migration.migrate(freshInstall)
            migration.cleanUp()
            migrated
        }
    }

    private fun keysRemovedBy(cleanUp: () -> Unit): Set<String> {
        clearLegacyPreferences(context)
        writeLegacyPreferences(context)

        val before = legacyCommonsFile(context).all.keys.toSet()
        cleanUp()

        return before - legacyCommonsFile(context).all.keys
    }

    private fun assertNoLegacyFileLeft(order: String) {
        assertFalse(order, legacyCommonsFileExists(context))

        CameraMode.entries.forEach { mode ->
            assertFalse("$order, ${mode.name}", legacyModeFileExists(context, mode))
        }
    }

    private enum class Owner {
        SETTINGS,
        STORAGE,
        MEDIA,
    }

    private data class Migrated(
        val settings: SettingsPrefs? = null,
        val storage: StoragePrefs? = null,
        val media: MediaPrefs? = null,
    )

    private companion object {
        val ORDERS = listOf(
            listOf(Owner.SETTINGS, Owner.STORAGE, Owner.MEDIA),
            listOf(Owner.SETTINGS, Owner.MEDIA, Owner.STORAGE),
            listOf(Owner.STORAGE, Owner.SETTINGS, Owner.MEDIA),
            listOf(Owner.STORAGE, Owner.MEDIA, Owner.SETTINGS),
            listOf(Owner.MEDIA, Owner.SETTINGS, Owner.STORAGE),
            listOf(Owner.MEDIA, Owner.STORAGE, Owner.SETTINGS),
        )
    }
}
