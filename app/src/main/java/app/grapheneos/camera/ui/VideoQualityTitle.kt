package app.grapheneos.camera.ui

import android.content.Context
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.VideoQuality

fun videoQualityTitle(context: Context, quality: VideoQuality): String {
    val titleId = when (quality) {
        VideoQuality.UHD -> R.string.video_quality_uhd
        VideoQuality.FHD -> R.string.video_quality_fhd
        VideoQuality.HD -> R.string.video_quality_hd
        VideoQuality.SD -> R.string.video_quality_sd
        VideoQuality.HIGHEST -> R.string.video_quality_unknown
    }

    return context.getString(titleId)
}
