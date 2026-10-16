package app.grapheneos.camera.ui.gallery.components

import app.grapheneos.camera.data.media.model.MediaTimestamp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

internal fun MediaTimestamp.formatted(
    locale: Locale,
    zone: ZoneId,
): String {
    return when (this) {
        // A name records wall-clock time and not the zone it was read in, so naming a zone for it
        // would assert the wrong one after the device travels
        is MediaTimestamp.WallClock -> localFormatter(locale).format(dateTime)
        is MediaTimestamp.Absolute -> zonedFormatter(locale).format(instant.atZone(zone))
    }
}

private fun zonedFormatter(locale: Locale): DateTimeFormatter {
    return DateTimeFormatterBuilder()
        .appendLocalized(FormatStyle.MEDIUM, FormatStyle.MEDIUM)
        .appendLiteral(' ')
        .appendZoneText(TextStyle.SHORT)
        .toFormatter(locale)
}

private fun localFormatter(locale: Locale): DateTimeFormatter {
    return DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(locale)
}
