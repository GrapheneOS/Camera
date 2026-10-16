package app.grapheneos.camera.data.media.mapper

import app.grapheneos.camera.data.media.model.CapturedItem
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale
import javax.inject.Inject

interface CaptureTimeMapper {
    /**
     * The capture time [item]'s own name encodes. It is the last record of when media was taken
     * once its Exif has been stripped and the provider keeps no creation timestamp, as the Storage
     * Access Framework never does. Null if the name carries no usable timestamp.
     */
    fun map(item: CapturedItem): LocalDateTime?
}

internal class CaptureTimeMapperImpl @Inject constructor() : CaptureTimeMapper {

    override fun map(item: CapturedItem): LocalDateTime? {
        // ImageSaver appends milliseconds to the name and CreateRecordingOutput does not
        return parse(item.dateString, MILLISECONDS_FORMAT) ?: parse(item.dateString, SECONDS_FORMAT)
    }

    private fun parse(dateString: String, format: DateTimeFormatter): LocalDateTime? {
        return try {
            LocalDateTime.parse(dateString, format)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private companion object {
        private val MILLISECONDS_FORMAT = strictFormat("uuuuMMdd_HHmmss_SSS")
        private val SECONDS_FORMAT = strictFormat("uuuuMMdd_HHmmss")

        private fun strictFormat(pattern: String): DateTimeFormatter {
            return DateTimeFormatter
                .ofPattern(pattern, Locale.US)
                .withResolverStyle(ResolverStyle.STRICT)
        }
    }
}
