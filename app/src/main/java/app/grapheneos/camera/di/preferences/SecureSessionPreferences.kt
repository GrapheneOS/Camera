package app.grapheneos.camera.di.preferences

import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Singleton
internal class SecureSessionPreferences @Inject constructor() {

    private var storage: DataStore<StoragePrefs>? = null
    private var sessionSettingsRepository: SettingsRepository? = null
    private var openActivities = 0

    fun settingsRepository(owners: SettingsRepository): SettingsRepository {
        return sessionSettingsRepository
            ?: owners.sessionCopy().also { sessionSettingsRepository = it }
    }

    fun storageSnapshotOf(durable: DataStore<StoragePrefs>): DataStore<StoragePrefs> {
        return storage ?: snapshotOf(durable).also { storage = it }
    }

    fun onSecureActivityCreated() {
        openActivities++
    }

    fun onSecureActivityDestroyed(isChangingConfigurations: Boolean) {
        openActivities--

        if (openActivities > 0) return

        openActivities = 0

        // The ViewModel outlives a configuration change and keeps writing to the copy it was built
        // with, so the recreated Activity has to be handed that same copy rather than a fresh one.
        if (isChangingConfigurations) return

        storage = null
        sessionSettingsRepository = null
    }

    private fun <T> snapshotOf(durable: DataStore<T>): DataStore<T> {
        return InMemoryDataStore(runBlocking { durable.data.first() })
    }
}
