package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.video.Recorder
import javax.inject.Inject

interface HdrVideoSupport {

    fun isSupported(cameraInfo: CameraInfo): Boolean
}

internal class HdrVideoSupportImpl @Inject constructor() : HdrVideoSupport {

    override fun isSupported(cameraInfo: CameraInfo): Boolean {
        val dynamicRanges = Recorder.getVideoCapabilities(cameraInfo).supportedDynamicRanges

        return DynamicRange.HLG_10_BIT in dynamicRanges
    }
}
