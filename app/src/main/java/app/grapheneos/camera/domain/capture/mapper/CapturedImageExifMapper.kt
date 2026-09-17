package app.grapheneos.camera.domain.capture.mapper

import androidxc.camera.core.impl.utils.Exif
import app.grapheneos.camera.clearExif
import app.grapheneos.camera.domain.capture.model.CapturedImageExif
import app.grapheneos.camera.fixExif
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject

interface CapturedImageExifMapper {

    fun map(input: CapturedImageExif): ByteArray
}

// based on EXIF update sequence in androidx.camera.core.ImageSaver#saveImageToTempFile(),
// optimized to skip writing of the unfinished image to storage
internal class CapturedImageExifMapperImpl @Inject constructor() : CapturedImageExifMapper {

    override fun map(input: CapturedImageExif): ByteArray {
        val exif = Exif.createFromInputStream(ByteArrayInputStream(input.jpegBytes))

        if (input.isCropped) {
            val uncropped = Exif.createFromInputStream(
                ByteArrayInputStream(input.uncroppedJpegBytes),
            )
            uncropped.copyToCroppedImage(exif)
        }

        // Overwrite the original orientation if the quirk exists.
        if (!input.shouldUseExifOrientation) {
            exif.rotate(input.orientationDegrees)
        }

        if (input.metadata.reversedHorizontal) {
            exif.flipHorizontally()
        }

        if (input.metadata.reversedVertical) {
            exif.flipVertically()
        }

        val exifInterface = exif.exifInterface

        if (input.removeExif) {
            // TODO improve clearExif() by moving it into ExifInterface
            exifInterface.clearExif()
        } else {
            exifInterface.fixExif(input.captureTime)
        }

        // location metadata setting intentionally ignores the "clear EXIF after capture" setting
        input.metadata.location?.let {
            exif.attachLocation(it)
        }

        val output = ByteArrayOutputStream(input.jpegBytes.size + ADDED_ATTRIBUTES_HEADROOM)
        exifInterface.saveAttributes(ByteArrayInputStream(input.jpegBytes), output)

        return output.toByteArray()
    }

    private companion object {
        // make sure buffer doesn't need to be resized due to additional EXIF attributes
        private const val ADDED_ATTRIBUTES_HEADROOM = 100 * 1024
    }
}
