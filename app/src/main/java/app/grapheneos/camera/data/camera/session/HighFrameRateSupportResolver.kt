package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraInfo
import androidx.camera.core.SessionConfig
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.lifecycle.ProcessCameraProvider
import app.grapheneos.camera.data.camera.model.HighFrameRateLimit
import app.grapheneos.camera.data.camera.model.HighFrameRateProbeKey
import app.grapheneos.camera.data.camera.model.HighFrameRateSupport
import app.grapheneos.camera.data.core.model.AspectRatio
import javax.inject.Inject

interface HighFrameRateSupportResolver {

    fun resolve(
        provider: ProcessCameraProvider,
        cameraInfo: CameraInfo,
        key: HighFrameRateProbeKey,
        aspectRatio: AspectRatio,
    ): HighFrameRateSupport
}

internal class HighFrameRateSupportResolverImpl @Inject constructor(
    private val cameraSessionPlanFactory: CameraSessionPlanFactory,
) : HighFrameRateSupportResolver {

    private val verdicts = HashMap<HighFrameRateProbeKey, HighFrameRateSupport>()

    private var probedProvider: ProcessCameraProvider? = null

    override fun resolve(
        provider: ProcessCameraProvider,
        cameraInfo: CameraInfo,
        key: HighFrameRateProbeKey,
        aspectRatio: AspectRatio,
    ): HighFrameRateSupport {
        if (probedProvider !== provider) {
            verdicts.clear()
            probedProvider = provider
        }

        return verdicts.getOrPut(key) {
            support(
                key = key,
                isSupported = { features ->
                    cameraInfo.isSessionConfigSupported(
                        SessionConfig(
                            useCases = cameraSessionPlanFactory.createProbeUseCases(aspectRatio),
                            requiredFeatureGroup = features,
                        ),
                    )
                },
            )
        }
    }

    private fun support(
        key: HighFrameRateProbeKey,
        isSupported: (Set<GroupableFeature>) -> Boolean,
    ): HighFrameRateSupport {
        val withoutHdr = setOfNotNull(
            key.videoQualityFeature,
            GroupableFeature.FPS_60,
        )
        val withHdr = when {
            key.hdrVideo -> withoutHdr + GroupableFeature.HDR_HLG10
            else -> withoutHdr
        }

        return when {
            isSupported(withHdr) -> HighFrameRateSupport.Supported

            key.hdrVideo && isSupported(withoutHdr) -> {
                HighFrameRateSupport.Unavailable(limit = HighFrameRateLimit.HDR)
            }

            else -> HighFrameRateSupport.Unavailable(limit = HighFrameRateLimit.RESOLUTION)
        }
    }
}
