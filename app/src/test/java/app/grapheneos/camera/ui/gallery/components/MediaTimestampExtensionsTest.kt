package app.grapheneos.camera.ui.gallery.components

import app.grapheneos.camera.data.media.model.MediaTimestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTimestampExtensionsTest {

    @Test
    fun localDateTime_absolute_isTheInstantInTheGivenZone() {
        val timestamp = MediaTimestamp.Absolute(Instant.parse("2023-10-15T10:34:56Z"))

        assertEquals(LocalDateTime.parse("2023-10-15T10:34:56"), timestamp.localDateTime(UTC))
        assertEquals(LocalDateTime.parse("2023-10-15T12:34:56"), timestamp.localDateTime(BERLIN))
    }

    @Test
    fun localDateTime_wallClock_keepsItsDigitsInAnyZone() {
        val timestamp = MediaTimestamp.WallClock(LocalDateTime.parse("2023-10-15T12:34:56"))

        assertEquals(LocalDateTime.parse("2023-10-15T12:34:56"), timestamp.localDateTime(UTC))
        assertEquals(LocalDateTime.parse("2023-10-15T12:34:56"), timestamp.localDateTime(BERLIN))
    }

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
