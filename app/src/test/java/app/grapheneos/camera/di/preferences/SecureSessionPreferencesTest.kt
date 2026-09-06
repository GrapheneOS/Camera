package app.grapheneos.camera.di.preferences

import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.StoredCameraSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class SecureSessionPreferencesTest {

    private val secureSession = SecureSessionPreferences()

    private val durable: DataStore<SettingsPrefs> = InMemoryDataStore(
        SettingsPrefs(common = StoredCameraSettings(photoQuality = OWNERS_PHOTO_QUALITY)),
    )

    private fun photoQualityIn(store: DataStore<SettingsPrefs>): Int? {
        return runBlocking { store.data.first() }.common.photoQuality
    }

    private fun write(store: DataStore<SettingsPrefs>, quality: Int) {
        runBlocking {
            store.updateData { it.copy(common = it.common.copy(photoQuality = quality)) }
        }
    }

    @Test
    fun everyActivityOfOneSession_sharesOneSnapshot() {
        secureSession.onSecureActivityCreated()
        val viewfinder = secureSession.settingsSnapshotOf(durable)

        secureSession.onSecureActivityCreated()
        val settingsScreen = secureSession.settingsSnapshotOf(durable)

        assertSame(viewfinder, settingsScreen)
    }

    @Test
    fun aWriteInOneActivity_reachesTheOthersButNotTheOwner() {
        secureSession.onSecureActivityCreated()
        val settingsScreen = secureSession.settingsSnapshotOf(durable)
        secureSession.onSecureActivityCreated()
        val viewfinder = secureSession.settingsSnapshotOf(durable)

        write(settingsScreen, SESSIONS_PHOTO_QUALITY)

        assertEquals(SESSIONS_PHOTO_QUALITY, photoQualityIn(viewfinder))
        assertEquals(OWNERS_PHOTO_QUALITY, photoQualityIn(durable))
    }

    @Test
    fun aSessionThatStillHasAnActivity_keepsItsSnapshot() {
        secureSession.onSecureActivityCreated()
        val viewfinder = secureSession.settingsSnapshotOf(durable)
        secureSession.onSecureActivityCreated()

        // The settings screen of the same session closes; the viewfinder is still there.
        secureSession.onSecureActivityDestroyed()

        assertSame(viewfinder, secureSession.settingsSnapshotOf(durable))
    }

    @Test
    fun theNextSession_startsFromWhatTheOwnerStored() {
        secureSession.onSecureActivityCreated()
        write(secureSession.settingsSnapshotOf(durable), SESSIONS_PHOTO_QUALITY)
        secureSession.onSecureActivityDestroyed()

        secureSession.onSecureActivityCreated()
        val nextSession = secureSession.settingsSnapshotOf(durable)

        assertEquals(OWNERS_PHOTO_QUALITY, photoQualityIn(nextSession))
    }

    @Test
    fun theNextSession_getsAStoreOfItsOwn() {
        secureSession.onSecureActivityCreated()
        val firstSession = secureSession.settingsSnapshotOf(durable)
        secureSession.onSecureActivityDestroyed()

        secureSession.onSecureActivityCreated()

        assertNotSame(firstSession, secureSession.settingsSnapshotOf(durable))
    }

    @Test
    fun anUnbalancedDestroy_doesNotStrandTheCounter() {
        secureSession.onSecureActivityDestroyed()

        secureSession.onSecureActivityCreated()
        val session = secureSession.settingsSnapshotOf(durable)
        secureSession.onSecureActivityDestroyed()
        secureSession.onSecureActivityCreated()

        assertNotSame(session, secureSession.settingsSnapshotOf(durable))
    }

    private companion object {
        const val OWNERS_PHOTO_QUALITY = 70

        const val SESSIONS_PHOTO_QUALITY = 95
    }
}
