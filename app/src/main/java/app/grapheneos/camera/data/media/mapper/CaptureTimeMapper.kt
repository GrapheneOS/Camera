package app.grapheneos.camera.data.media.mapper

import app.grapheneos.camera.data.media.model.CapturedItem
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

interface CaptureTimeMapper {
    /**
     * The capture time [item]'s own name encodes, in milliseconds. It is the last record of when
     * media was taken once its Exif has been stripped and the provider keeps no creation timestamp,
     * as the Storage Access Framework never does. Null if the name carries no usable timestamp.
     */
    fun map(item: CapturedItem): Long?
}

internal class CaptureTimeMapperImpl @Inject constructor() : CaptureTimeMapper {

    override fun map(item: CapturedItem): Long? {
        // ImageSaver appends milliseconds to the name and CreateRecordingOutput does not. Both
        // name in whatever the default time zone was at capture, which nothing records, so the name
        // is read back in the current one: the wall-clock digits survive a change of zone, the
        // instant does not. Callers must not present this as a zoned timestamp.
        val dateString = item.dateString

        return parse(dateString, MILLISECONDS_PATTERN) ?: parse(dateString, SECONDS_PATTERN)
    }

    private fun parse(dateString: String, pattern: String): Long? {
        val format = SimpleDateFormat(pattern, Locale.US)
        format.isLenient = false

        return try {
            format.parse(dateString)?.time
        } catch (e: ParseException) {
            null
        }
    }

    private companion object {
        private const val MILLISECONDS_PATTERN = "yyyyMMdd_HHmmss_SSS"
        private const val SECONDS_PATTERN = "yyyyMMdd_HHmmss"
    }
}
