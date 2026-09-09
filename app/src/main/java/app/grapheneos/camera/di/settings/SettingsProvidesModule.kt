package app.grapheneos.camera.di.settings

import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.di.core.DurablePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal class SettingsProvidesModule {

    @Provides
    @Singleton
    @DurablePreferences
    fun provideDurableSettingsRepository(
        @DurablePreferences dataStore: DataStore<SettingsPrefs>,
        factory: SettingsRepositoryFactory,
    ): SettingsRepository {
        return factory.create(dataStore)
    }
}
