package app.grapheneos.camera.di.preferences

import android.app.Activity
import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.ui.activities.MoreSettings
import app.grapheneos.camera.ui.activities.MoreSettingsSecure
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesProvidesModuleTest {

    private val module = PreferencesProvidesModule()

    private val secureSession = SecureSessionPreferences()

    private val owners = mockk<SettingsRepository> {
        every { sessionCopy() } answers { mockk() }
    }

    private val durableStorage: DataStore<StoragePrefs> = InMemoryDataStore(StoragePrefs())

    @Test
    fun settingsRepository_secureActivity_isACopyOfTheOwners() {
        runTest {
            val session = settingsRepositoryFor(MoreSettingsSecure::class.java)

            assertNotSame(owners, session)
            verify(exactly = 1) { owners.sessionCopy() }
        }
    }

    @Test
    fun settingsRepository_regularActivity_isTheOwners() {
        runTest {
            val session = settingsRepositoryFor(MoreSettings::class.java)

            assertSame(owners, session)
            verify(exactly = 0) { owners.sessionCopy() }
        }
    }

    @Test
    fun storagePrefs_secureActivity_keepsWritesOutOfTheOwnersStore() {
        runTest {
            val session = storagePrefsFor(MoreSettingsSecure::class.java)

            session.updateData { it.copy(storageLocation = SESSIONS_LOCATION) }

            assertEquals(SESSIONS_LOCATION, stored(session).storageLocation)
            assertNull(stored(durableStorage).storageLocation)
        }
    }

    @Test
    fun storagePrefs_secureActivity_doesNotFollowTheOwnersLaterChanges() {
        runTest {
            val session = storagePrefsFor(MoreSettingsSecure::class.java)

            durableStorage.updateData { it.copy(storageLocation = OWNERS_LOCATION) }

            assertNull(stored(session).storageLocation)
        }
    }

    @Test
    fun storagePrefs_regularActivity_writesTheOwnersStore() {
        runTest {
            val session = storagePrefsFor(MoreSettings::class.java)

            session.updateData { it.copy(storageLocation = OWNERS_LOCATION) }

            assertEquals(OWNERS_LOCATION, stored(durableStorage).storageLocation)
        }
    }

    private fun <T : Activity> settingsRepositoryFor(type: Class<T>): SettingsRepository {
        return module.provideSettingsRepository(
            context = Robolectric.buildActivity(type).get(),
            owners = owners,
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

    private suspend fun <T> stored(from: DataStore<T>): T {
        return from.data.first()
    }

    private companion object {
        const val OWNERS_LOCATION = "content://tree/owners"
        const val SESSIONS_LOCATION = "content://tree/sessions"
    }
}
