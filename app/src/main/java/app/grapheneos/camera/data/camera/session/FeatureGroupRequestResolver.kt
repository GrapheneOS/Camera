package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraInfo
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.lifecycle.ProcessCameraProvider
import app.grapheneos.camera.data.camera.model.FeatureGroupRequest
import app.grapheneos.camera.data.camera.model.HighFrameRateProbeKey
import app.grapheneos.camera.data.camera.model.HighFrameRateSupport
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.model.LensFeatures
import app.grapheneos.camera.data.camera.model.VideoBindSettings
import app.grapheneos.camera.data.camera.model.VideoFeatureGroup
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.VideoFrameRate
import javax.inject.Inject

interface FeatureGroupRequestResolver {

    fun resolve(
        provider: ProcessCameraProvider,
        cameraInfo: CameraInfo?,
        lensFacing: LensFacing,
        aspectRatio: AspectRatio,
        video: VideoBindSettings?,
        videoQualityFeature: GroupableFeature?,
        features: LensFeatures,
    ): VideoFeatureGroup
}

internal class FeatureGroupRequestResolverImpl @Inject constructor(
    private val highFrameRateSupportResolver: HighFrameRateSupportResolver,
) : FeatureGroupRequestResolver {

    // CameraX 1.6.0's SessionConfig throws an IllegalArgumentException at construction time
    // if any use case configures a groupable feature through a non-groupable API while a
    // feature group is in use. Recorder.Builder.setQualitySelector() is such an API since
    // 1.6.0 introduced GroupableFeatures.*_RECORDING, so when EIS is requested through
    // GroupableFeature.PREVIEW_STABILIZATION (the only case where this app uses a feature
    // group), the video quality has to be requested through the feature group as well.
    //
    // The validation triggers on setQualitySelector() having been called at all, not on the
    // quality it was given, so this flag -- not the resolved feature below -- is what decides
    // whether that setter may be used. videoQualityAsGroupableFeature() can legitimately fail
    // to map the current quality, and falling back to setQualitySelector() in that case would
    // reintroduce the very exception this works around.
    //
    // Both halves of LensFeatures.isVideoStabilizationSupported gate the group. The platform
    // has to be able to verify feature combinations: where it cannot, the resolver would
    // conflate "could not check" with "unsupported", quietly discard the stored video quality and produce
    // untruthful notices. Cameras behind that gate use the pre-1.6 non-groupable setters in
    // the session plan instead, which are legal exactly because no feature group is in use
    // then. And the group exists solely to negotiate stabilization, HDR and 60 fps against the
    // quality, so a camera that can apply none of them has nothing to negotiate: it takes the
    // plain fallback path directly, which produces the identical output without a needless
    // feature-group round. HDR is never applied off the group either: the strict quality
    // selector there would fail the bind at any quality the camera can't record in HLG.
    override fun resolve(
        provider: ProcessCameraProvider,
        cameraInfo: CameraInfo?,
        lensFacing: LensFacing,
        aspectRatio: AspectRatio,
        video: VideoBindSettings?,
        videoQualityFeature: GroupableFeature?,
        features: LensFeatures,
    ): VideoFeatureGroup {
        if (video == null) return VideoFeatureGroup(request = FeatureGroupRequest.Unused)

        val requestsStabilization = video.stabilization && features.isVideoStabilizationSupported
        val requestsHdr = video.hdr && features.isHdrVideoSupported
        val highFrameRate = cameraInfo
            ?.takeIf { features.isHighFrameRateSupported }
            ?.let {
                highFrameRateSupportResolver.resolve(
                    provider = provider,
                    cameraInfo = it,
                    key = HighFrameRateProbeKey(
                        lensFacing = lensFacing,
                        videoQualityFeature = videoQualityFeature,
                        hdrVideo = requestsHdr,
                    ),
                    aspectRatio = aspectRatio,
                )
            }
        val requestsHighFrameRate = video.frameRate == VideoFrameRate.FPS_60 &&
            highFrameRate == HighFrameRateSupport.Supported
        val requestsAny = requestsStabilization || requestsHdr || requestsHighFrameRate

        return VideoFeatureGroup(
            request = when {
                requestsAny -> FeatureGroupRequest.Requested(
                    videoQualityFeature = videoQualityFeature,
                    stabilization = requestsStabilization,
                    hdr = requestsHdr,
                    highFrameRate = requestsHighFrameRate,
                )

                else -> FeatureGroupRequest.Unused
            },
            highFrameRate = highFrameRate,
        )
    }
}
