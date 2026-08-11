package app.grapheneos.camera.data.settings.model

import androidx.camera.video.Quality

// These are the persisted form of the video quality setting as well as the displayed one: the
// per-mode preferences hold the title string rather than the Quality itself, so changing one
// orphans every install that had picked it.
//
// TODO: store the Quality by name rather than by label, so the labels can move into strings.xml.
// It needs a read-time migration of every label already stored, so it waits for the settings
// screen rewrite that owns this setting.
private const val TITLE_UHD = "2160p (UHD)"
private const val TITLE_FHD = "1080p (FHD)"
private const val TITLE_HD = "720p (HD)"
private const val TITLE_SD = "480p (SD)"
private const val TITLE_UNKNOWN = "Unknown"

fun videoQualityTitle(quality: Quality): String {
    return storableVideoQualityTitle(quality) ?: TITLE_UNKNOWN
}

/**
 * The title [quality] may be stored under, or null for one that has none: [Quality.HIGHEST] and
 * [Quality.LOWEST] name whatever the device offers rather than a resolution, and storing their
 * placeholder title would read back through [videoQualityFromTitle] as SD.
 */
fun storableVideoQualityTitle(quality: Quality): String? {
    return when (quality) {
        Quality.UHD -> TITLE_UHD
        Quality.FHD -> TITLE_FHD
        Quality.HD -> TITLE_HD
        Quality.SD -> TITLE_SD
        else -> null
    }
}

fun videoQualityFromTitle(title: String): Quality {
    return when (title) {
        TITLE_UHD -> Quality.UHD
        TITLE_FHD -> Quality.FHD
        TITLE_HD -> Quality.HD
        TITLE_SD -> Quality.SD
        else -> Quality.SD
    }
}
