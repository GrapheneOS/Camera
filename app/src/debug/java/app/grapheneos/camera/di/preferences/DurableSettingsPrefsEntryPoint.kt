package app.grapheneos.camera.di.preferences

import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.di.core.DurablePreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DurableSettingsPrefsEntryPoint {

    @DurablePreferences
    fun settingsPrefs(): DataStore<SettingsPrefs>
}
