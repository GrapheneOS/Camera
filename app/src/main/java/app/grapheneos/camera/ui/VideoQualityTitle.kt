package app.grapheneos.camera.ui

import android.content.Context
import androidx.camera.video.Quality
import app.grapheneos.camera.R

fun videoQualityTitle(context: Context, quality: Quality): String {
    val titleId = when (quality) {
        Quality.UHD -> R.string.video_quality_uhd
        Quality.FHD -> R.string.video_quality_fhd
        Quality.HD -> R.string.video_quality_hd
        Quality.SD -> R.string.video_quality_sd
        else -> R.string.video_quality_unknown
    }

    return context.getString(titleId)
}
