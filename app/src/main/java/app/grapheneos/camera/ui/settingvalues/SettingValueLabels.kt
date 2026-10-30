package app.grapheneos.camera.ui.settingvalues

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import kotlin.time.Duration

@Composable
internal fun flashLabel(mode: FlashMode): String {
    return when (mode) {
        FlashMode.OFF -> stringResource(R.string.quick_settings_off)
        FlashMode.AUTO -> stringResource(R.string.quick_settings_auto)
        FlashMode.ON -> stringResource(R.string.quick_settings_on)
    }
}

internal fun flashIcon(mode: FlashMode): ImageVector {
    return when (mode) {
        FlashMode.OFF -> Icons.Outlined.FlashOff
        FlashMode.AUTO -> Icons.Outlined.FlashAuto
        FlashMode.ON -> Icons.Outlined.FlashOn
    }
}

@Composable
internal fun selfTimerLabel(duration: Duration): String {
    return when (duration) {
        Duration.ZERO -> stringResource(R.string.quick_settings_off)
        else -> stringResource(R.string.quick_settings_seconds, duration.inWholeSeconds.toInt())
    }
}

@Composable
internal fun videoQualityLabel(quality: VideoQuality): String {
    return when (quality) {
        VideoQuality.UHD -> stringResource(R.string.video_quality_uhd_short)
        VideoQuality.FHD -> stringResource(R.string.video_quality_fhd_short)
        VideoQuality.HD -> stringResource(R.string.video_quality_hd_short)
        VideoQuality.SD -> stringResource(R.string.video_quality_sd_short)
        VideoQuality.HIGHEST -> stringResource(R.string.video_quality_unknown)
    }
}
