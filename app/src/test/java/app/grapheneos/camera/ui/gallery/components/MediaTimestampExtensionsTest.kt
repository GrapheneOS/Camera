package app.grapheneos.camera.ui.gallery.components

import app.grapheneos.camera.data.media.model.MediaTimestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTimestampExtensionsTest {

    @Test
    fun formatted_absolute_showsTheInstantInTheGivenZoneAndNamesIt() {
        val timestamp = MediaTimestamp.Absolute(Instant.parse("2023-10-15T10:34:56Z"))

        assertEquals("15.10.2023, 10:34:56 UTC", timestamp.formatted(Locale.GERMANY, UTC))
        assertEquals("15.10.2023, 12:34:56 MESZ", timestamp.formatted(Locale.GERMANY, BERLIN))
    }

    @Test
    fun formatted_wallClock_keepsItsDigitsAndNamesNoZone() {
        val timestamp = MediaTimestamp.WallClock(LocalDateTime.parse("2023-10-15T12:34:56"))

        assertEquals("15.10.2023, 12:34:56", timestamp.formatted(Locale.GERMANY, UTC))
        assertEquals("15.10.2023, 12:34:56", timestamp.formatted(Locale.GERMANY, BERLIN))
    }

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
