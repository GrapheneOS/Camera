package app.grapheneos.camera.data.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.data.core.model.CameraMode
import java.io.File

internal const val LEGACY_COMMON_PREFS_NAME = "commons"
internal const val LEGACY_MEDIA_PREFS_NAME = "media"

internal val LEGACY_CAPTURE_KEY_NAMES = setOf(
    "last_captured_item_type",
    "last_captured_item_date_string",
    "last_captured_item_uri",
)

internal val LEGACY_STORAGE_KEY_NAMES = setOf(
    "storage_location",
    "previous_saf_trees",
    "media_uri_s",
)

internal const val LEGACY_ITEM_DATE_STRING = "20260724_153012_345"
internal const val LEGACY_ITEM_URI = "content://media/external/images/media/1"

internal val LEGACY_COMMON_ENTRIES: Map<String, Any> = mapOf(
    "aspect_ratio" to 1,
    "camera_sounds" to false,
    "emphasis_on_quality" to true,
    "enable_eis" to true,
    "enable_zsl" to true,
    "focus_timeout" to "10s",
    "grid" to 3,
    "gyroscope_suggestions" to true,
    "include_audio" to false,
    "media_uri_s" to "content://tree/a/document/photo.jpg",
    "photo_quality" to 71,
    "previous_saf_trees" to "content://tree/a\u0000content://tree/b",
    "remove_exif_after_capture" to true,
    "save_image_as_preview" to true,
    "save_video_as_preview" to true,
    "scan_all_codes" to true,
    "scan_AZTEC" to true,
    "scan_CODE_39" to false,
    "scan_QR_CODE" to true,
    "select_highest_resolution" to true,
    "self_timer_duration" to 5,
    "storage_location" to "content://tree/current",
    "wait_for_focus_lock" to false,
    "last_captured_item_type" to ITEM_TYPE_IMAGE,
    "last_captured_item_date_string" to LEGACY_ITEM_DATE_STRING,
    "last_captured_item_uri" to LEGACY_ITEM_URI,
)

internal val LEGACY_MODE_ENTRIES: Map<String, Any> = mapOf(
    "flash_mode" to 1,
    "geo_tagging" to true,
    "self_illumination" to true,
    "video_quality_BACK" to "1080p (FHD)",
    "video_quality_FRONT" to "720p (HD)",
)

internal fun legacyCommonsFile(context: Context): SharedPreferences {
    return context.getSharedPreferences(LEGACY_COMMON_PREFS_NAME, Context.MODE_PRIVATE)
}

internal fun legacyModeFile(context: Context, mode: CameraMode): SharedPreferences {
    return context.getSharedPreferences(mode.name, Context.MODE_PRIVATE)
}

internal fun legacyMediaFile(context: Context): SharedPreferences {
    return context.getSharedPreferences(LEGACY_MEDIA_PREFS_NAME, Context.MODE_PRIVATE)
}

internal fun writeLegacyPreferences(context: Context) {
    legacyCommonsFile(context).edit(commit = true) {
        LEGACY_COMMON_ENTRIES.forEach { (key, value) -> put(key, value) }
    }

    CameraMode.entries.forEach { mode ->
        legacyModeFile(context, mode).edit(commit = true) {
            LEGACY_MODE_ENTRIES.forEach { (key, value) -> put(key, value) }
        }
    }
}

internal fun legacyCommonsFileExists(context: Context): Boolean {
    return legacyFile(context, LEGACY_COMMON_PREFS_NAME).exists()
}

internal fun legacyModeFileExists(context: Context, mode: CameraMode): Boolean {
    return legacyFile(context, mode.name).exists()
}

internal fun legacyMediaFileExists(context: Context): Boolean {
    return legacyFile(context, LEGACY_MEDIA_PREFS_NAME).exists()
}

private fun legacyFile(context: Context, name: String): File {
    return File(context.dataDir, "shared_prefs/$name.xml")
}

internal fun clearLegacyPreferences(context: Context) {
    legacyCommonsFile(context).edit(commit = true) { clear() }
    context.deleteSharedPreferences(LEGACY_COMMON_PREFS_NAME)

    legacyMediaFile(context).edit(commit = true) { clear() }
    context.deleteSharedPreferences(LEGACY_MEDIA_PREFS_NAME)

    CameraMode.entries.forEach { mode ->
        legacyModeFile(context, mode).edit(commit = true) { clear() }
        context.deleteSharedPreferences(mode.name)
    }
}

private fun SharedPreferences.Editor.put(key: String, value: Any) {
    when (value) {
        is Boolean -> putBoolean(key, value)
        is Int -> putInt(key, value)
        is String -> putString(key, value)
        else -> error("a legacy file never held a ${value::class.simpleName}")
    }
}
