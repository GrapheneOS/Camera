package app.grapheneos.camera.di.preferences

import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.di.core.DurablePreferences
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal class PreferencesViewModelProvidesModule {

    @Provides
    @ViewModelScoped
    fun provideSettingsRepository(
        entryPoint: CameraEntryPoint,
        @DurablePreferences owners: SettingsRepository,
        secureSession: SecureSessionPreferences,
    ): SettingsRepository {
        return when {
            entryPoint.isSecureSession -> secureSession.settingsRepository(owners)
            else -> owners
        }
    }

    @Provides
    @ViewModelScoped
    fun provideStoragePrefs(
        entryPoint: CameraEntryPoint,
        @DurablePreferences durable: DataStore<StoragePrefs>,
        secureSession: SecureSessionPreferences,
    ): DataStore<StoragePrefs> {
        return when {
            entryPoint.isSecureSession -> secureSession.storageSnapshotOf(durable)
            else -> durable
        }
    }
}
