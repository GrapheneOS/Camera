package app.grapheneos.camera.data.settings.model

import androidx.camera.video.Quality

// TODO: move these into strings.xml, which they could not be while they were also the storage
// format. They wait for the settings screen rewrite that owns this setting.
private const val TITLE_UHD = "2160p (UHD)"
private const val TITLE_FHD = "1080p (FHD)"
private const val TITLE_HD = "720p (HD)"
private const val TITLE_SD = "480p (SD)"
private const val TITLE_UNKNOWN = "Unknown"

fun videoQualityTitle(quality: Quality): String {
    return when (quality) {
        Quality.UHD -> TITLE_UHD
        Quality.FHD -> TITLE_FHD
        Quality.HD -> TITLE_HD
        Quality.SD -> TITLE_SD
        else -> TITLE_UNKNOWN
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
