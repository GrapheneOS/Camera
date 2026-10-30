package app.grapheneos.camera.data.camera.session

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.ImageCapture
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.video.GroupableFeatures
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.internal.muxer.MediaMuxerImpl
import app.grapheneos.camera.data.camera.mapper.CameraXConstantsMapper
import app.grapheneos.camera.data.camera.model.CameraBindRequest
import app.grapheneos.camera.data.camera.model.CameraSessionPlan
import app.grapheneos.camera.data.camera.model.FeatureGroupRequest
import app.grapheneos.camera.data.camera.model.PhotoBindSettings
import app.grapheneos.camera.data.camera.model.VideoBindSettings
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CaptureMode
import javax.inject.Inject

interface CameraSessionPlanFactory {

    fun create(request: CameraBindRequest): CameraSessionPlan

    /** Nothing in these is set through a non-groupable API, so a feature group can be probed. */
    fun createProbeUseCases(aspectRatio: AspectRatio): List<UseCase>
}

internal class CameraSessionPlanFactoryImpl @Inject constructor(
    private val cameraXConstantsMapper: CameraXConstantsMapper,
) : CameraSessionPlanFactory {

    override fun create(request: CameraBindRequest): CameraSessionPlan {
        val aspectRatioStrategy = aspectRatioStrategy(request.aspectRatio)

        return CameraSessionPlan(
            videoCapture = request.video?.let { video ->
                buildVideoCapture(
                    video = video,
                    featureGroup = request.featureGroup,
                )
            },
            imageCapture = request.photo?.let { photo ->
                buildImageCapture(
                    photo = photo,
                    ultraHdr = photo.ultraHdr && request.lensFeatures.isUltraHdrSupported,
                    targetRotation = request.imageCaptureTargetRotation,
                    aspectRatioStrategy = aspectRatioStrategy,
                )
            },
            preview = buildPreview(
                request = request,
                aspectRatioStrategy = aspectRatioStrategy,
            ),
            preferredFeatures = preferredFeatures(request),
        )
    }

    override fun createProbeUseCases(aspectRatio: AspectRatio): List<UseCase> {
        val preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspectRatioStrategy(aspectRatio))
                    .build(),
            )
            .build()

        return listOf(
            preview,
            VideoCapture.withOutput(Recorder.Builder().build()),
        )
    }

    private fun aspectRatioStrategy(aspectRatio: AspectRatio): AspectRatioStrategy {
        return AspectRatioStrategy(
            cameraXConstantsMapper.map(aspectRatio),
            AspectRatioStrategy.FALLBACK_RULE_AUTO,
        )
    }

    @SuppressLint("RestrictedApi")
    private fun buildVideoCapture(
        video: VideoBindSettings,
        featureGroup: FeatureGroupRequest,
    ): VideoCapture<Recorder> {
        val recorderBuilder = Recorder.Builder()

        // camera-video 1.6 writes mp4 through the media3 muxer, which cannot keep up with
        // 2160p on a Tensor device: the audio queue overflows a few seconds in and stop
        // then has to drain everything the muxer is behind by. The platform muxer, which
        // is what every release up to 1.5 used, keeps up. Both live in an internal
        // package, so this has to be re-checked on every camera-video upgrade.
        recorderBuilder.setMuxerFactory { MediaMuxerImpl() }

        when (featureGroup) {
            is FeatureGroupRequest.Requested -> {}
            FeatureGroupRequest.Unused -> {
                val quality = cameraXConstantsMapper.map(video.quality)
                recorderBuilder.setQualitySelector(QualitySelector.from(quality))
            }
        }

        val videoCaptureBuilder = VideoCapture.Builder(recorderBuilder.build())

        // On cameras where the feature group is not used (see CameraFeatureSupport)
        // EIS is deliberately left off. The pre-1.6 stabilization setters cannot be applied
        // on top of the recorder's higher qualities -- UHD in particular is in none of the
        // stabilization-guaranteed configurations -- so requesting them would either kill
        // the preview or force the quality down. We keep the selected quality (4K by
        // default) and simply do not stabilize. This is the app's long-standing behavior
        // (EIS regressed here when it moved off the Camera2 API); the toggle is hidden
        // rather than left inert on these cameras, and implementing EIS for them -- the
        // pre-1.6 setters, restricted to the qualities that permit them -- is a separate
        // change that needs a device the feature group cannot serve to test on.

        if (video.mirrorOnFrontCamera) {
            videoCaptureBuilder.setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
        }

        return videoCaptureBuilder.build()
    }

    private fun buildImageCapture(
        photo: PhotoBindSettings,
        ultraHdr: Boolean,
        targetRotation: Int,
        aspectRatioStrategy: AspectRatioStrategy,
    ): ImageCapture {
        val resolutionSelectorBuilder = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)

        if (photo.selectHighestResolution) {
            resolutionSelectorBuilder.setAllowedResolutionMode(
                ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE,
            )
        }

        val imageCaptureBuilder = ImageCapture.Builder()
            .setCaptureMode(cameraXCaptureMode(photo.captureMode))
            .setTargetRotation(targetRotation)
            .setResolutionSelector(resolutionSelectorBuilder.build())
            .setFlashMode(cameraXConstantsMapper.map(photo.flashMode))
            .setJpegQuality(photo.quality.percent)

        if (ultraHdr) {
            imageCaptureBuilder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
        }

        return imageCaptureBuilder.build()
    }

    @OptIn(ExperimentalZeroShutterLag::class)
    private fun cameraXCaptureMode(captureMode: CaptureMode): Int {
        return when (captureMode) {
            CaptureMode.SPEED -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            CaptureMode.QUALITY -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            CaptureMode.ZERO_SHUTTER_LAG -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
        }
    }

    private fun buildPreview(
        request: CameraBindRequest,
        aspectRatioStrategy: AspectRatioStrategy,
    ): Preview {
        val previewBuilder = Preview.Builder()
            .setTargetRotation(request.previewTargetRotation)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspectRatioStrategy)
                    .build(),
            )

        // Pixels and potentially other devices enable EIS by default, which reduces the field of
        // view and image quality for image capture if it's not explicitly disabled.
        //
        // setPreviewStabilizationEnabled() is one of the non-groupable setters that SessionConfig
        // rejects outright once a feature group is in use, so it must not be called at all --
        // with either value -- when a feature group is requested below, for stabilization or
        // HDR. On every other path it is explicitly disabled: EIS is not applied off the feature
        // group (see the VideoCapture builder above), and leaving it unset would let a device
        // default preview stabilization cost photo capture its field of view.
        when (request.featureGroup) {
            is FeatureGroupRequest.Requested -> {}
            FeatureGroupRequest.Unused -> previewBuilder.setPreviewStabilizationEnabled(false)
        }

        return previewBuilder.build()
    }

    // The list ordering encodes priority (highest priority first): CameraX walks the subsets
    // of this list in order and binds the first one the camera supports, so trailing features
    // are the ones given up first.
    //
    // The video quality leads because it is an explicit, deliberate choice from a spinner,
    // whereas stabilization is a toggle that defaults to on and that most users never touch;
    // an explicit choice should not lose to a default. HDR is off unless turned on, so it
    // outranks stabilization but not the quality. 60 fps is only requested once it has been
    // checked to fit the quality and HDR (see HighFrameRateSupportResolver), so it comes right after
    // them and still ahead of stabilization. This inverts the CameraX 1.5.x
    // behaviour, where asking for 2160p on a Pixel silently recorded at 1080p because
    // stabilization won. Whatever is given up is now reported by onFeaturesSelected().
    //
    // Both stabilization features are listed because the EIS toggle is offered whenever
    // either kind is supported (see CameraFeatureSupport). They share one feature
    // type, so CameraX never selects both and skips the subsets containing the pair; the
    // effective order is quality+preview-stabilization, quality+video-stabilization, quality
    // alone, then the same three without the quality. Preview stabilization is preferred
    // because it stabilizes the preview and the recording alike, making the framing that is
    // shown the framing that is recorded, while video stabilization only stabilizes the file.
    //
    // If the quality feature is dropped the quality follows Recorder's default quality
    // selector (FHD, HD, SD in that order).
    private fun preferredFeatures(request: CameraBindRequest): List<GroupableFeature> {
        val featureGroup = request.featureGroup
        if (featureGroup !is FeatureGroupRequest.Requested) return emptyList()

        return buildList {
            addAll(featureGroup.chosenFeatures())

            if (featureGroup.stabilization) {
                add(GroupableFeature.PREVIEW_STABILIZATION)
                add(GroupableFeatures.VIDEO_STABILIZATION)
            }
        }
    }
}
