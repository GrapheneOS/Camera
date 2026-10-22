package app.grapheneos.camera.ui.gallery.components

import android.text.format.DateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TIME_SKELETON = "hm"
private const val TIME_24_HOUR_SKELETON = "Hm"

internal fun LocalDateTime.formatted(
    locale: Locale,
    skeleton: String,
): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)

    return DateTimeFormatter
        .ofPattern(pattern, locale)
        .format(this)
}

internal fun LocalDateTime.formattedTime(
    locale: Locale,
    is24Hour: Boolean,
): String {
    return formatted(
        locale = locale,
        skeleton = when {
            is24Hour -> TIME_24_HOUR_SKELETON
            else -> TIME_SKELETON
        },
    )
}
