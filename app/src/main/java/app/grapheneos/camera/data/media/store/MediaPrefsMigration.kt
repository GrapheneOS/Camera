package app.grapheneos.camera.data.media.store

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import app.grapheneos.camera.data.core.store.LEGACY_CAPTURE_KEYS
import app.grapheneos.camera.data.core.store.LEGACY_LAST_CAPTURED_ITEM_DATE_STRING
import app.grapheneos.camera.data.core.store.LEGACY_LAST_CAPTURED_ITEM_TYPE
import app.grapheneos.camera.data.core.store.LEGACY_LAST_CAPTURED_ITEM_URI
import app.grapheneos.camera.data.core.store.legacyCommonPreferences
import app.grapheneos.camera.data.core.store.removeLegacyCommonKeys

// The stores migrate the shared file in arbitrary order, so each removes only its own keys.
internal class MediaPrefsMigration(
    private val context: Context,
) : DataMigration<MediaPrefs> {

    override suspend fun shouldMigrate(currentData: MediaPrefs): Boolean {
        return legacyMediaPreferences().all.keys.any(::owns) ||
            legacyCommonPreferences(context).all.keys.any(::owns)
    }

    override suspend fun migrate(currentData: MediaPrefs): MediaPrefs {
        val stored = currentData.lastCapturedItem
            ?: readLastCapturedItem(legacyMediaPreferences())
            ?: readLastCapturedItem(legacyCommonPreferences(context))

        return currentData.copy(lastCapturedItem = stored)
    }

    override suspend fun cleanUp() {
        context.deleteSharedPreferences(LEGACY_MEDIA_PREFS_NAME)
        removeLegacyCommonKeys(context, ::owns)
    }

    private fun owns(key: String): Boolean {
        return key in LEGACY_CAPTURE_KEYS
    }

    private fun readLastCapturedItem(preferences: SharedPreferences): StoredCapturedItem? {
        val dateString = preferences.getString(LEGACY_LAST_CAPTURED_ITEM_DATE_STRING, null)
        val uri = preferences.getString(LEGACY_LAST_CAPTURED_ITEM_URI, null)

        return when {
            dateString == null || uri == null -> null
            else -> {
                StoredCapturedItem(
                    type = preferences.getInt(LEGACY_LAST_CAPTURED_ITEM_TYPE, -1),
                    dateString = dateString,
                    uri = uri,
                )
            }
        }
    }

    private fun legacyMediaPreferences(): SharedPreferences {
        return context.getSharedPreferences(LEGACY_MEDIA_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private companion object {
        const val LEGACY_MEDIA_PREFS_NAME = "media"
    }
}
