package app.grapheneos.camera.di.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.di.core.DurablePreferences
import app.grapheneos.camera.ui.activities.SecureActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Module
@InstallIn(ActivityComponent::class)
internal class PreferencesProvidesModule {

    @Provides
    @ActivityScoped
    fun provideSettingsPrefs(
        @ActivityContext context: Context,
        @DurablePreferences durable: DataStore<SettingsPrefs>,
    ): DataStore<SettingsPrefs> {
        return when (context) {
            // Secure sessions get a snapshot so later owner changes cannot leak through the lockscreen.
            is SecureActivity -> InMemoryDataStore(runBlocking { durable.data.first() })
            else -> durable
        }
    }

    @Provides
    @ActivityScoped
    fun provideStoragePrefs(
        @ActivityContext context: Context,
        @DurablePreferences durable: DataStore<StoragePrefs>,
    ): DataStore<StoragePrefs> {
        return when (context) {
            is SecureActivity -> InMemoryDataStore(runBlocking { durable.data.first() })
            else -> durable
        }
    }
}
