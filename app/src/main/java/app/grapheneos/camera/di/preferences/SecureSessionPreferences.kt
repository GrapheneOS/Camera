package app.grapheneos.camera.di.preferences

import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Singleton
internal class SecureSessionPreferences @Inject constructor() {

    private var settings: DataStore<SettingsPrefs>? = null
    private var storage: DataStore<StoragePrefs>? = null
    private var sessionSettingsRepository: SettingsRepository? = null
    private var openActivities = 0

    fun settingsSnapshotOf(durable: DataStore<SettingsPrefs>): DataStore<SettingsPrefs> {
        return settings ?: snapshotOf(durable).also { settings = it }
    }

    fun settingsRepository(
        durable: DataStore<SettingsPrefs>,
        create: (DataStore<SettingsPrefs>) -> SettingsRepository,
    ): SettingsRepository {
        return sessionSettingsRepository
            ?: create(settingsSnapshotOf(durable)).also { sessionSettingsRepository = it }
    }

    fun storageSnapshotOf(durable: DataStore<StoragePrefs>): DataStore<StoragePrefs> {
        return storage ?: snapshotOf(durable).also { storage = it }
    }

    fun onSecureActivityCreated() {
        openActivities++
    }

    fun onSecureActivityDestroyed() {
        openActivities--

        if (openActivities <= 0) {
            openActivities = 0
            settings = null
            storage = null
            sessionSettingsRepository = null
        }
    }

    private fun <T> snapshotOf(durable: DataStore<T>): DataStore<T> {
        return InMemoryDataStore(runBlocking { durable.data.first() })
    }
}
