package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraInfo
import androidx.camera.core.Preview
import androidx.camera.video.Recorder
import javax.inject.Inject

interface VideoStabilizationSupport {

    fun isSupported(cameraInfo: CameraInfo): Boolean
}

internal class VideoStabilizationSupportImpl @Inject constructor() : VideoStabilizationSupport {

    // The toggle asks for both kinds of stabilization (see the preferred feature group in
    // bind), preferring the preview kind and falling back to the recording-only kind,
    // so it is meaningful whenever either one is available. Testing only the recorder
    // capability both hid the toggle on cameras that can stabilize the preview but not the
    // recording, and offered it on cameras where only the recorder can stabilize - where
    // the bind used to ask exclusively for preview stabilization, leaving the toggle with
    // nothing to do.
    override fun isSupported(cameraInfo: CameraInfo): Boolean {
        return Preview.getPreviewCapabilities(cameraInfo).isStabilizationSupported ||
            Recorder.getVideoCapabilities(cameraInfo).isStabilizationSupported
    }
}
