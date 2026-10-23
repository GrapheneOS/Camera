package app.grapheneos.camera.ui.gallery.components.details

import android.content.Context
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CameraParameters
import app.grapheneos.camera.data.media.model.CapturedItemDetails
import app.grapheneos.camera.util.formatVideoDuration
import app.grapheneos.camera.util.storageLocationToUiString
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private const val PIXELS_PER_MEGAPIXEL = 1_000_000f
private const val UHD_SHORT_SIDE = 2160
private const val FULL_HD_SHORT_SIDE = 1080
private const val HD_SHORT_SIDE = 720
private const val MEGAPIXEL_PATTERN = "0.0"
private const val PARAMETER_PATTERN = "0.##"

@Composable
internal fun fileFacts(
    details: CapturedItemDetails,
    isVideo: Boolean,
): List<String> {
    val locale = LocalLocale.current.platformLocale
    val resources = LocalResources.current

    return remember(details, isVideo, locale, resources) {
        val resolution = details.resolution
        val duration = details.duration?.takeIf { isVideo }

        listOfNotNull(
            resolution?.let {
                when {
                    isVideo -> videoQuality(it)?.let(resources::getString)
                    else -> resources.getString(
                        R.string.gallery_details_megapixels,
                        decimalFormat(MEGAPIXEL_PATTERN, locale).format(megapixels(it)),
                    )
                }
            },
            resolution?.let {
                resources.getString(R.string.gallery_details_resolution, it.width, it.height)
            },
            duration?.let {
                formatVideoDuration(it.inWholeSeconds)
            },
        )
    }
}

@Composable
internal fun cameraName(camera: CameraParameters): String? {
    val make = camera.make
    val model = camera.model

    return when {
        model == null -> make
        make == null || model.startsWith(make, ignoreCase = true) -> model
        else -> stringResource(R.string.gallery_details_camera, make, model)
    }
}

@Composable
internal fun exposureSettings(camera: CameraParameters): List<String> {
    val locale = LocalLocale.current.platformLocale
    val resources = LocalResources.current

    return remember(camera, locale, resources) {
        val format = decimalFormat(PARAMETER_PATTERN, locale)

        listOfNotNull(
            camera.fNumber?.let {
                resources.getString(R.string.gallery_details_aperture, format.format(it))
            },
            camera.exposureSeconds?.let {
                when {
                    it < 1.0 -> resources.getString(
                        R.string.gallery_details_exposure_fraction,
                        (1.0 / it).roundToInt(),
                    )
                    else -> resources.getString(
                        R.string.gallery_details_exposure_seconds,
                        format.format(it),
                    )
                }
            },
            camera.focalLengthMillimeters?.let {
                resources.getString(R.string.gallery_details_focal_length, format.format(it))
            },
            camera.iso?.let {
                resources.getString(R.string.gallery_details_iso, it)
            },
        )
    }
}

@Composable
internal fun modifiedText(details: CapturedItemDetails): String? {
    val created = details.created?.let { dateTimeText(it) }
    val modified = details.modified?.let { dateTimeText(it) }

    return modified
        ?.takeIf { created != null && it != created }
        ?.let { stringResource(R.string.gallery_details_modified, it) }
}

internal fun folder(
    context: Context,
    uri: String,
    details: CapturedItemDetails,
): String {
    return when (val relativePath = details.relativePath) {
        null -> storageLocationToUiString(context, uri)
        else -> "${context.getString(R.string.main_storage)}/${relativePath.trimEnd('/')}"
    }
}

internal fun decimalFormat(
    pattern: String,
    locale: Locale,
): DecimalFormat {
    return DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale))
}

private fun megapixels(resolution: Size): Float {
    return resolution.width * resolution.height / PIXELS_PER_MEGAPIXEL
}

private fun videoQuality(resolution: Size): Int? {
    val shortSide = min(resolution.width, resolution.height)

    return when {
        shortSide >= UHD_SHORT_SIDE -> R.string.gallery_details_4k
        shortSide >= FULL_HD_SHORT_SIDE -> R.string.gallery_details_full_hd
        shortSide >= HD_SHORT_SIDE -> R.string.gallery_details_hd
        else -> null
    }
}
