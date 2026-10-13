package app.grapheneos.camera.ui.camerasettings.screen

import android.text.format.Formatter
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.camerasettings.components.formatMegapixels
import app.grapheneos.camera.ui.camerasettings.components.formatPhotoQuality
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoResolutionUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.StorageSpaceUiModel
import java.text.NumberFormat

@Composable
internal fun photoQualitySummary(photoQuality: PhotoQualityUiModel): String {
    val percent = formatPhotoQuality(photoQuality.quality)
    val compression = stringResource(photoQuality.level.label)
    val resolution = photoQuality.resolution
    val resolutionLabel = when {
        resolution != null -> megapixels(
            resolution = resolution,
            isFull = photoQuality.isFullResolution,
        )

        photoQuality.isFullResolution -> stringResource(
            R.string.camera_settings_photo_quality_full_resolution,
        )

        else -> null
    }

    return when (resolutionLabel) {
        null -> stringResource(
            R.string.camera_settings_photo_quality_summary,
            compression,
            percent,
        )

        else -> stringResource(
            R.string.camera_settings_photo_quality_summary_with_resolution,
            resolutionLabel,
            compression,
            percent,
        )
    }
}

@Composable
internal fun megapixels(
    resolution: PhotoResolutionUiModel,
    isFull: Boolean,
): String {
    val megapixels = when {
        isFull -> resolution.fullMegapixels
        else -> resolution.standardMegapixels
    }

    return formatMegapixels(megapixels)
}

@Composable
internal fun storageSpaceTitle(storageSpace: StorageSpaceUiModel): String? {
    val freeBytes = storageSpace.freeBytes ?: return null

    return stringResource(
        R.string.camera_settings_free_space,
        Formatter.formatFileSize(LocalContext.current, freeBytes),
    )
}

@Composable
internal fun storageSpaceEstimate(storageSpace: StorageSpaceUiModel): String? {
    val photos = storageSpace.photos?.let { count ->
        countLabel(R.plurals.camera_settings_photo_count, count)
    }
    val videoMinutes = storageSpace.videoMinutes?.let { count ->
        countLabel(R.plurals.camera_settings_video_minute_count, count)
    }
    val estimate = when {
        photos != null && videoMinutes != null -> {
            stringResource(R.string.camera_settings_photos_or_video, photos, videoMinutes)
        }

        else -> photos ?: videoMinutes
    }

    return estimate?.let { stringResource(R.string.camera_settings_free_space_estimate, it) }
}

@Composable
internal fun countLabel(
    @PluralsRes label: Int,
    count: Long,
): String {
    return pluralStringResource(
        label,
        count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        NumberFormat.getIntegerInstance().format(count),
    )
}

@Composable
internal fun focusTimeoutLabel(seconds: Long): String {
    return when (seconds) {
        0L -> stringResource(R.string.camera_settings_focus_timeout_off)
        else -> pluralStringResource(
            R.plurals.camera_settings_seconds,
            seconds.toInt(),
            seconds,
        )
    }
}
