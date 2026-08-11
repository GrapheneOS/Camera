package app.grapheneos.camera.data.core.store

import android.content.Context
import android.content.SharedPreferences
import app.grapheneos.camera.util.EphemeralSharedPrefs

/** Everything the owner configured that is not scoped to one camera mode. */
private const val COMMON_PREFS_NAME = "commons"

private const val MEDIA_PREFS_NAME = "media"

/** Shared with the media feature. Empty means the MediaStore, which holds no SAF grant. */
internal const val STORAGE_LOCATION_KEY = "storage_location"

internal fun commonPreferences(
    context: Context,
    ephemeral: Boolean,
): SharedPreferences {
    return preferences(
        context = context,
        name = COMMON_PREFS_NAME,
        ephemeral = ephemeral,
    )
}

/** Never ephemeral: a lockscreen capture is a real file the owner's gallery has to point at. */
internal fun mediaPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(MEDIA_PREFS_NAME, Context.MODE_PRIVATE)
}

private fun preferences(
    context: Context,
    name: String,
    ephemeral: Boolean,
): SharedPreferences {
    return when {
        ephemeral -> EphemeralSharedPrefs.copyOf(context = context, name = name)
        else -> context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }
}
