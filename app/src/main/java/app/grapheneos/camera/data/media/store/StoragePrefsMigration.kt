package app.grapheneos.camera.data.media.store

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import app.grapheneos.camera.data.core.store.LEGACY_MEDIA_URIS
import app.grapheneos.camera.data.core.store.LEGACY_PREVIOUS_SAF_TREES
import app.grapheneos.camera.data.core.store.LEGACY_STORAGE_KEYS
import app.grapheneos.camera.data.core.store.LEGACY_STORAGE_LOCATION
import app.grapheneos.camera.data.core.store.legacyCommonPreferences
import app.grapheneos.camera.data.core.store.removeLegacyCommonKeys

// The stores migrate the shared file in arbitrary order, so each removes only its own keys.
internal class StoragePrefsMigration(
    private val context: Context,
) : DataMigration<StoragePrefs> {

    override suspend fun shouldMigrate(currentData: StoragePrefs): Boolean {
        return legacyCommonPreferences(context).all.keys.any(::owns)
    }

    override suspend fun migrate(currentData: StoragePrefs): StoragePrefs {
        val commons = legacyCommonPreferences(context)

        return currentData.copy(
            storageLocation = stringOrNull(commons, LEGACY_STORAGE_LOCATION)
                ?: currentData.storageLocation,
            previousSafTrees = readPreviousSafTrees(commons)
                ?: currentData.previousSafTrees,
            legacyMediaUris = stringOrNull(commons, LEGACY_MEDIA_URIS)
                ?: currentData.legacyMediaUris,
        )
    }

    override suspend fun cleanUp() {
        removeLegacyCommonKeys(context, ::owns)
    }

    private fun owns(key: String): Boolean {
        return key in LEGACY_STORAGE_KEYS
    }

    private fun readPreviousSafTrees(commons: SharedPreferences): List<String>? {
        val stored = stringOrNull(commons, LEGACY_PREVIOUS_SAF_TREES) ?: return null

        return stored.split(SAF_TREE_SEPARATOR).filter { it.isNotEmpty() }
    }

    private fun stringOrNull(preferences: SharedPreferences, key: String): String? {
        return when {
            preferences.contains(key) -> preferences.getString(key, null)
            else -> null
        }
    }

    private companion object {
        // This separator belongs to the shipped legacy format.
        const val SAF_TREE_SEPARATOR = "\u0000"
    }
}
