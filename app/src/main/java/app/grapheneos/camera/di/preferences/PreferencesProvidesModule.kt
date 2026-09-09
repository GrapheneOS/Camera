package app.grapheneos.camera.di.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.di.core.DurablePreferences
import app.grapheneos.camera.di.settings.SettingsRepositoryFactory
import app.grapheneos.camera.ui.activities.SecureActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal class PreferencesProvidesModule {

    @Provides
    @ActivityScoped
    fun provideSettingsPrefs(
        @ActivityContext context: Context,
        @DurablePreferences durable: DataStore<SettingsPrefs>,
        secureSession: SecureSessionPreferences,
    ): DataStore<SettingsPrefs> {
        return when (context) {
            // Secure sessions get a snapshot so later owner changes cannot leak through the lockscreen.
            is SecureActivity -> secureSession.settingsSnapshotOf(durable)
            else -> durable
        }
    }

    @Provides
    @ActivityScoped
    fun provideSettingsRepository(
        @ActivityContext context: Context,
        @DurablePreferences durable: SettingsRepository,
        @DurablePreferences durablePrefs: DataStore<SettingsPrefs>,
        secureSession: SecureSessionPreferences,
        factory: SettingsRepositoryFactory,
    ): SettingsRepository {
        return when (context) {
            is SecureActivity -> secureSession.settingsRepository(
                durable = durablePrefs,
                create = factory::create,
            )

            else -> durable
        }
    }

    @Provides
    @ActivityScoped
    fun provideStoragePrefs(
        @ActivityContext context: Context,
        @DurablePreferences durable: DataStore<StoragePrefs>,
        secureSession: SecureSessionPreferences,
    ): DataStore<StoragePrefs> {
        return when (context) {
            is SecureActivity -> secureSession.storageSnapshotOf(durable)
            else -> durable
        }
    }
}
