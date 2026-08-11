package app.grapheneos.camera.data.core.store

import android.content.Context
import android.content.SharedPreferences
import app.grapheneos.camera.data.core.model.CameraMode
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

/**
 * One file per mode, opened once: a second ephemeral copy would be re-read from the owner's file and
 * would silently discard everything the session had changed since the first.
 */
internal fun modePreferences(
    context: Context,
    ephemeral: Boolean,
): Map<CameraMode, Lazy<SharedPreferences>> {
    return CameraMode
        .entries
        .associateWith { mode ->
            lazy {
                preferences(
                    context = context,
                    name = mode.name,
                    ephemeral = ephemeral,
                )
            }
        }
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
