package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.video.Recorder
import app.grapheneos.camera.data.camera.model.LensFeatures
import javax.inject.Inject

interface CameraFeatureSupport {

    fun features(cameraInfo: CameraInfo): LensFeatures
}

internal class CameraFeatureSupportImpl @Inject constructor(
    private val featureCombinationSupport: FeatureCombinationSupport,
) : CameraFeatureSupport {

    // Stabilization and HDR video are only ever requested through the feature group (see bind),
    // which in turn needs the platform to be able to verify feature combinations, so a camera
    // that can stabilize or record HLG is not on its own enough: where the group cannot be used
    // nothing applies them, and offering the toggle there is offering a control that does
    // nothing.
    @androidx.annotation.OptIn(ExperimentalZeroShutterLag::class)
    override fun features(cameraInfo: CameraInfo): LensFeatures {
        val canUseFeatureGroup = featureCombinationSupport.canVerify(cameraInfo)

        return LensFeatures(
            isZslSupported = cameraInfo.isZslSupported,
            isVideoStabilizationSupported = canUseFeatureGroup && canStabilize(cameraInfo),
            isHdrVideoSupported = canUseFeatureGroup && canRecordHlg(cameraInfo),
            isUltraHdrSupported = canCaptureUltraHdr(cameraInfo),
        )
    }

    // The toggle asks for both kinds of stabilization (see the preferred feature group in
    // bind), preferring the preview kind and falling back to the recording-only kind,
    // so it is meaningful whenever either one is available. Testing only the recorder
    // capability both hid the toggle on cameras that can stabilize the preview but not the
    // recording, and offered it on cameras where only the recorder can stabilize - where
    // the bind used to ask exclusively for preview stabilization, leaving the toggle with
    // nothing to do.
    private fun canStabilize(cameraInfo: CameraInfo): Boolean {
        return Preview.getPreviewCapabilities(cameraInfo).isStabilizationSupported ||
            Recorder.getVideoCapabilities(cameraInfo).isStabilizationSupported
    }

    private fun canRecordHlg(cameraInfo: CameraInfo): Boolean {
        val dynamicRanges = Recorder.getVideoCapabilities(cameraInfo).supportedDynamicRanges

        return DynamicRange.HLG_10_BIT in dynamicRanges
    }

    private fun canCaptureUltraHdr(cameraInfo: CameraInfo): Boolean {
        val outputFormats = ImageCapture.getImageCaptureCapabilities(cameraInfo)
            .supportedOutputFormats

        return ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR in outputFormats
    }
}
