package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import javax.inject.Inject

interface UltraHdrSupport {

    fun isSupported(cameraInfo: CameraInfo): Boolean
}

internal class UltraHdrSupportImpl @Inject constructor() : UltraHdrSupport {

    override fun isSupported(cameraInfo: CameraInfo): Boolean {
        val outputFormats = ImageCapture.getImageCaptureCapabilities(cameraInfo)
            .supportedOutputFormats

        return ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR in outputFormats
    }
}
