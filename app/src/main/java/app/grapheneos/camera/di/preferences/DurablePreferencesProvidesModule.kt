package app.grapheneos.camera.di.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import app.grapheneos.camera.data.media.store.MediaPrefs
import app.grapheneos.camera.data.media.store.MediaPrefsMigration
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.media.store.StoragePrefsMigration
import app.grapheneos.camera.data.media.store.mediaPrefsSerializer
import app.grapheneos.camera.data.media.store.storagePrefsSerializer
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.SettingsPrefsMigration
import app.grapheneos.camera.data.settings.store.settingsPrefsSerializer
import app.grapheneos.camera.di.core.DurablePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal class DurablePreferencesProvidesModule {

    @Provides
    @Singleton
    @DurablePreferences
    fun provideDurableSettingsPrefs(
        @ApplicationContext context: Context,
    ): DataStore<SettingsPrefs> {
        return DataStoreFactory.create(
            serializer = settingsPrefsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { SettingsPrefs() },
            migrations = listOf(SettingsPrefsMigration(context)),
        ) {
            context.dataStoreFile(SETTINGS_PREFS_FILE_NAME)
        }
    }

    @Provides
    @Singleton
    @DurablePreferences
    fun provideDurableStoragePrefs(
        @ApplicationContext context: Context,
    ): DataStore<StoragePrefs> {
        return DataStoreFactory.create(
            serializer = storagePrefsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { StoragePrefs() },
            migrations = listOf(StoragePrefsMigration(context)),
        ) {
            context.dataStoreFile(STORAGE_PREFS_FILE_NAME)
        }
    }

    @Provides
    @Singleton
    fun provideDurableMediaPrefs(
        @ApplicationContext context: Context,
    ): DataStore<MediaPrefs> {
        return DataStoreFactory.create(
            serializer = mediaPrefsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { MediaPrefs() },
            migrations = listOf(MediaPrefsMigration(context)),
        ) {
            context.dataStoreFile(MEDIA_PREFS_FILE_NAME)
        }
    }

    private companion object {
        private const val SETTINGS_PREFS_FILE_NAME = "settings_prefs.json"
        private const val STORAGE_PREFS_FILE_NAME = "storage_prefs.json"
        private const val MEDIA_PREFS_FILE_NAME = "media_prefs.json"
    }
}
