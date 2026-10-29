package app.grapheneos.camera.ui.common

import android.content.Context
import androidx.annotation.StringRes
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.VideoQuality

fun videoQualityTitle(context: Context, quality: VideoQuality): String {
    return context.getString(videoQualityTitleId(quality))
}

@StringRes
fun videoQualityTitleId(quality: VideoQuality): Int {
    return when (quality) {
        VideoQuality.UHD -> R.string.video_quality_uhd
        VideoQuality.FHD -> R.string.video_quality_fhd
        VideoQuality.HD -> R.string.video_quality_hd
        VideoQuality.SD -> R.string.video_quality_sd
        VideoQuality.HIGHEST -> R.string.video_quality_unknown
    }
}
