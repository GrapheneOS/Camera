package app.grapheneos.camera.data.core.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

private const val LEGACY_COMMON_PREFS_NAME = "commons"

internal const val LEGACY_LAST_CAPTURED_ITEM_TYPE = "last_captured_item_type"
internal const val LEGACY_LAST_CAPTURED_ITEM_DATE_STRING = "last_captured_item_date_string"
internal const val LEGACY_LAST_CAPTURED_ITEM_URI = "last_captured_item_uri"

internal const val LEGACY_STORAGE_LOCATION = "storage_location"
internal const val LEGACY_PREVIOUS_SAF_TREES = "previous_saf_trees"
internal const val LEGACY_MEDIA_URIS = "media_uri_s"

// Settings claims every key not explicitly owned by capture or storage.
internal val LEGACY_CAPTURE_KEYS = setOf(
    LEGACY_LAST_CAPTURED_ITEM_TYPE,
    LEGACY_LAST_CAPTURED_ITEM_DATE_STRING,
    LEGACY_LAST_CAPTURED_ITEM_URI,
)

internal val LEGACY_STORAGE_KEYS = setOf(
    LEGACY_STORAGE_LOCATION,
    LEGACY_PREVIOUS_SAF_TREES,
    LEGACY_MEDIA_URIS,
)

internal fun legacyCommonPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(LEGACY_COMMON_PREFS_NAME, Context.MODE_PRIVATE)
}

internal fun removeLegacyCommonKeys(context: Context, owns: (String) -> Boolean) {
    val preferences = legacyCommonPreferences(context)
    val owned = preferences.all.keys.filter(owns)

    // An empty edit can recreate the file after another migration deletes it.
    if (owned.isNotEmpty()) {
        preferences.edit(commit = true) {
            owned.forEach { remove(it) }
        }
    }

    if (preferences.all.isEmpty()) {
        context.deleteSharedPreferences(LEGACY_COMMON_PREFS_NAME)
    }
}
