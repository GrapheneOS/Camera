package app.grapheneos.camera

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidxc.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * West of UTC, the offset a photo is stored with came out as "-7:00" or "-2:-30" instead of
 * "-07:00" or "-02:30", unlike the one the camera itself stores beside it.
 */
@RunWith(AndroidJUnit4::class)
class NegativeUtcOffsetRegressionTest {

    @Test
    fun photo_takenAtANegativeWholeHourOffset_storesItWithTwoDigitHours() {
        assertOffsetTimesAt("GMT-07:00", "-07:00")
    }

    @Test
    fun photo_takenAtANegativeOffsetWithMinutes_storesThemUnsigned() {
        assertOffsetTimesAt("GMT-02:30", "-02:30")
        assertOffsetTimesAt("GMT-09:30", "-09:30")
    }

    @Test
    fun photo_takenAtUtcOrEastOfIt_storesTheOffsetAsBefore() {
        assertOffsetTimesAt("GMT", "+00:00")
        assertOffsetTimesAt("GMT+05:45", "+05:45")
        assertOffsetTimesAt("GMT+12:00", "+12:00")
    }

    private fun assertOffsetTimesAt(zoneId: String, expected: String) {
        val saved = savedWithCaptureExifIn(TimeZone.getTimeZone(zoneId))
        for (tag in listOf(ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL)) {
            assertEquals("$tag at $zoneId", expected, saved.getAttribute(tag))
        }
    }

    /** What the saver does to the Exif of a photo when it isn't told to remove it. */
    private fun savedWithCaptureExifIn(zone: TimeZone): ExifInterface {
        val jpeg = noiseJpeg()
        val exif = ExifInterface(ByteArrayInputStream(jpeg))
        val defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(zone)
        try {
            exif.fixExif(Date())
        } finally {
            TimeZone.setDefault(defaultZone)
        }
        val saved = ByteArrayOutputStream()
        exif.saveAttributes(ByteArrayInputStream(jpeg), saved)
        return ExifInterface(ByteArrayInputStream(saved.toByteArray()))
    }
}
