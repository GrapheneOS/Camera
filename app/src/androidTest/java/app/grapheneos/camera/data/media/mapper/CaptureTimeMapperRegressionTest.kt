package app.grapheneos.camera.data.media.mapper

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The gallery falls back to the timestamp in an item's own name when it has no creation time to
 * show, which is what happens to a photo whose Exif was stripped and which is kept through the
 * Storage Access Framework.
 */
@RunWith(AndroidJUnit4::class)
class CaptureTimeMapperRegressionTest {

    private val mapper = CaptureTimeMapperImpl()
    private val nameMapper = CapturedItemNameMapperImpl()

    private val uri = Uri.parse("content://media/external/images/media/0")

    /** The instant the app would have named a capture after, in the zone it names captures in. */
    private fun wallClock(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millis: Int = 0,
    ): Long {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, second)
        calendar.set(Calendar.MILLISECOND, millis)
        return calendar.timeInMillis
    }

    @Test
    fun captureTime_readsTheMillisecondNameImageSaverWrites() {
        val item = CapturedItem(CapturedItemType.IMAGE, "20260724_153012_345", uri)
        assertEquals(wallClock(2026, 7, 24, 15, 30, 12, 345), mapper.map(item))
    }

    /**
     * Videos are named to the second. The millisecond pattern is tried first, so this also proves
     * it cannot swallow a shorter name and invent a sub-second part for it.
     */
    @Test
    fun captureTime_readsTheSecondPrecisionNameVideoCapturerWrites() {
        val item = CapturedItem(CapturedItemType.VIDEO, "20260724_153012", uri)
        assertEquals(wallClock(2026, 7, 24, 15, 30, 12), mapper.map(item))
    }

    /** Whatever the device's zone is, what the capturers write has to read back unchanged. */
    @Test
    fun captureTime_roundTripsTheNamesTheCapturersWrite() {
        val captured = Date(wallClock(2026, 2, 29, 23, 59, 59, 999))

        val imageName = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(captured)
        val imageItem = CapturedItem(CapturedItemType.IMAGE, imageName, uri)
        assertEquals(captured.time, mapper.map(imageItem))

        val videoName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(captured)
        val videoItem = CapturedItem(CapturedItemType.VIDEO, videoName, uri)
        // The video name has no milliseconds to give back
        assertEquals(captured.time / 1000 * 1000, mapper.map(videoItem))
    }

    /** A timestamp is only reported when the name really holds one. */
    @Test
    fun captureTime_isNullWhenTheNameHasNoUsableTimestamp() {
        val dateStrings = listOf(
            "00000000_000000",
            "20261301_153012",
            "20260732_153012",
            "20260724_253012",
            "20260724_156012",
            "202607_153012",
        )

        for (dateString in dateStrings) {
            val item = CapturedItem(CapturedItemType.IMAGE, dateString, uri)
            assertNull(dateString, mapper.map(item))
        }
    }

    /** The names the item parser accepts off the file system are the ones that have to work. */
    @Test
    fun captureTime_readsTheNamesTheItemParserAccepts() {
        val image = nameMapper.map("IMG_20260724_153012_345.jpg", uri)
        assertNotNull(image)
        assertEquals(wallClock(2026, 7, 24, 15, 30, 12, 345), mapper.map(image!!))

        val video = nameMapper.map("VID_20260724_153012.mp4", uri)
        assertNotNull(video)
        assertEquals(wallClock(2026, 7, 24, 15, 30, 12), mapper.map(video!!))
    }
}
