package app.grapheneos.camera.di.preferences

import android.app.Activity
import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.StoredCameraSettings
import app.grapheneos.camera.ui.activities.MoreSettings
import app.grapheneos.camera.ui.activities.MoreSettingsSecure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesProvidesModuleTest {

    private val module = PreferencesProvidesModule()

    private val secureSession = SecureSessionPreferences()

    private val durableSettings: DataStore<SettingsPrefs> = InMemoryDataStore(
        SettingsPrefs(common = StoredCameraSettings(photoQuality = OWNERS_PHOTO_QUALITY)),
    )

    private val durableStorage: DataStore<StoragePrefs> = InMemoryDataStore(StoragePrefs())

    private fun <T : Activity> settingsPrefsFor(type: Class<T>): DataStore<SettingsPrefs> {
        return module.provideSettingsPrefs(
            context = Robolectric.buildActivity(type).get(),
            durable = durableSettings,
            secureSession = secureSession,
        )
    }

    private fun <T : Activity> storagePrefsFor(type: Class<T>): DataStore<StoragePrefs> {
        return module.provideStoragePrefs(
            context = Robolectric.buildActivity(type).get(),
            durable = durableStorage,
            secureSession = secureSession,
        )
    }

    private fun <T> stored(from: DataStore<T>): T {
        return runBlocking { from.data.first() }
    }

    @Test
    fun settingsPrefs_secureActivity_keepsWritesOutOfTheOwnersStore() {
        val session = settingsPrefsFor(MoreSettingsSecure::class.java)

        runBlocking {
            session.updateData {
                it.copy(common = it.common.copy(photoQuality = SESSIONS_PHOTO_QUALITY))
            }
        }

        assertEquals(SESSIONS_PHOTO_QUALITY, stored(session).common.photoQuality)
        assertEquals(OWNERS_PHOTO_QUALITY, stored(durableSettings).common.photoQuality)
    }

    @Test
    fun settingsPrefs_secureActivity_startsFromWhatTheOwnerConfigured() {
        val session = settingsPrefsFor(MoreSettingsSecure::class.java)

        assertEquals(OWNERS_PHOTO_QUALITY, stored(session).common.photoQuality)
    }

    @Test
    fun settingsPrefs_secureActivity_doesNotFollowTheOwnersLaterChanges() {
        val session = settingsPrefsFor(MoreSettingsSecure::class.java)

        runBlocking {
            durableSettings.updateData {
                it.copy(common = it.common.copy(photoQuality = SESSIONS_PHOTO_QUALITY))
            }
        }

        assertEquals(OWNERS_PHOTO_QUALITY, stored(session).common.photoQuality)
    }

    @Test
    fun settingsPrefs_regularActivity_writesTheOwnersStore() {
        val session = settingsPrefsFor(MoreSettings::class.java)

        runBlocking {
            session.updateData {
                it.copy(common = it.common.copy(photoQuality = SESSIONS_PHOTO_QUALITY))
            }
        }

        assertEquals(SESSIONS_PHOTO_QUALITY, stored(durableSettings).common.photoQuality)
    }

    @Test
    fun storagePrefs_secureActivity_keepsWritesOutOfTheOwnersStore() {
        val session = storagePrefsFor(MoreSettingsSecure::class.java)

        runBlocking { session.updateData { it.copy(storageLocation = SESSIONS_LOCATION) } }

        assertEquals(SESSIONS_LOCATION, stored(session).storageLocation)
        assertNull(stored(durableStorage).storageLocation)
    }

    @Test
    fun storagePrefs_secureActivity_doesNotFollowTheOwnersLaterChanges() {
        val session = storagePrefsFor(MoreSettingsSecure::class.java)

        runBlocking { durableStorage.updateData { it.copy(storageLocation = OWNERS_LOCATION) } }

        assertNull(stored(session).storageLocation)
    }

    @Test
    fun storagePrefs_regularActivity_writesTheOwnersStore() {
        val session = storagePrefsFor(MoreSettings::class.java)

        runBlocking { session.updateData { it.copy(storageLocation = OWNERS_LOCATION) } }

        assertEquals(OWNERS_LOCATION, stored(durableStorage).storageLocation)
    }

    private companion object {
        const val OWNERS_PHOTO_QUALITY = 71
        const val SESSIONS_PHOTO_QUALITY = 42
        const val OWNERS_LOCATION = "content://tree/owners"
        const val SESSIONS_LOCATION = "content://tree/sessions"
    }
}
