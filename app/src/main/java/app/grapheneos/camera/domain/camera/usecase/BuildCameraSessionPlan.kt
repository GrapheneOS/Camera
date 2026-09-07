package app.grapheneos.camera.domain.camera.usecase

import android.annotation.SuppressLint
import androidx.camera.core.ImageCapture
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.video.GroupableFeatures
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.internal.muxer.MediaMuxerImpl
import app.grapheneos.camera.domain.camera.model.CameraBindRequest
import app.grapheneos.camera.domain.camera.model.CameraSessionPlan
import app.grapheneos.camera.domain.camera.model.FeatureGroupRequest
import app.grapheneos.camera.domain.camera.model.ImageCaptureMode
import javax.inject.Inject

interface BuildCameraSessionPlan {

    operator fun invoke(request: CameraBindRequest): CameraSessionPlan
}

@SuppressLint("UnsafeOptInUsageError", "RestrictedApi")
internal class BuildCameraSessionPlanImpl @Inject constructor() : BuildCameraSessionPlan {

    override fun invoke(request: CameraBindRequest): CameraSessionPlan {
        val aspectRatioStrategy = AspectRatioStrategy(
            request.aspectRatio,
            AspectRatioStrategy.FALLBACK_RULE_AUTO,
        )

        val captureMode = when {
            request.waitForFocusLock -> ImageCaptureMode.MAXIMIZE_QUALITY
            request.enableZsl -> ImageCaptureMode.ZERO_SHUTTER_LAG
            else -> ImageCaptureMode.MINIMIZE_LATENCY
        }

        return CameraSessionPlan(
            videoCapture = when {
                request.includesVideoCapture -> buildVideoCapture(request)
                else -> null
            },
            imageCapture = when {
                request.includesImageCapture -> buildImageCapture(
                    request = request,
                    captureMode = captureMode,
                    aspectRatioStrategy = aspectRatioStrategy,
                )

                else -> null
            },
            preview = buildPreview(
                request = request,
                aspectRatioStrategy = aspectRatioStrategy,
            ),
            captureMode = captureMode,
            preferredFeatures = preferredFeatures(request),
        )
    }

    private fun buildVideoCapture(request: CameraBindRequest): VideoCapture<Recorder> {
        val recorderBuilder = Recorder.Builder()

        // camera-video 1.6 writes mp4 through the media3 muxer, which cannot keep up with
        // 2160p on a Tensor device: the audio queue overflows a few seconds in and stop
        // then has to drain everything the muxer is behind by. The platform muxer, which
        // is what every release up to 1.5 used, keeps up. Both live in an internal
        // package, so this has to be re-checked on every camera-video upgrade.
        recorderBuilder.setMuxerFactory { MediaMuxerImpl() }

        when (request.featureGroup) {
            is FeatureGroupRequest.Requested -> {}
            FeatureGroupRequest.Unused -> {
                recorderBuilder.setQualitySelector(QualitySelector.from(request.videoQuality))
            }
        }

        val videoCaptureBuilder = VideoCapture.Builder(recorderBuilder.build())

        // On cameras where the feature group is not used (see canApplyVideoStabilization)
        // EIS is deliberately left off. The pre-1.6 stabilization setters cannot be applied
        // on top of the recorder's higher qualities -- UHD in particular is in none of the
        // stabilization-guaranteed configurations -- so requesting them would either kill
        // the preview or force the quality down. We keep the selected quality (4K by
        // default) and simply do not stabilize. This is the app's long-standing behavior
        // (EIS regressed here when it moved off the Camera2 API); the toggle is hidden
        // rather than left inert on these cameras, and implementing EIS for them -- the
        // pre-1.6 setters, restricted to the qualities that permit them -- is a separate
        // change that needs a device the feature group cannot serve to test on.

        if (request.mirrorVideoOnFrontCamera) {
            videoCaptureBuilder.setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
        }

        return videoCaptureBuilder.build()
    }

    private fun buildImageCapture(
        request: CameraBindRequest,
        captureMode: ImageCaptureMode,
        aspectRatioStrategy: AspectRatioStrategy,
    ): ImageCapture {
        val resolutionSelectorBuilder = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)

        if (request.selectHighestResolution) {
            resolutionSelectorBuilder.setAllowedResolutionMode(
                ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
            )
        }

        return ImageCapture.Builder()
            .setCaptureMode(cameraXCaptureMode(captureMode))
            .setTargetRotation(request.imageCaptureTargetRotation)
            .setResolutionSelector(resolutionSelectorBuilder.build())
            .setFlashMode(request.flashMode)
            .setJpegQuality(request.photoQuality)
            .build()
    }

    private fun cameraXCaptureMode(captureMode: ImageCaptureMode): Int {
        return when (captureMode) {
            ImageCaptureMode.MAXIMIZE_QUALITY -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            ImageCaptureMode.ZERO_SHUTTER_LAG -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
            ImageCaptureMode.MINIMIZE_LATENCY -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
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
                    .build()
            )

        // Pixels and potentially other devices enable EIS by default, which reduces the field of
        // view and image quality for image capture if it's not explicitly disabled.
        //
        // setPreviewStabilizationEnabled() is one of the non-groupable setters that SessionConfig
        // rejects outright once a feature group is in use, so it must not be called at all --
        // with either value -- when stabilization is being requested through the feature group
        // below. On every other path it is explicitly disabled: EIS is not applied off the feature
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
    // an explicit choice should not lose to a default. This inverts the CameraX 1.5.x
    // behaviour, where asking for 2160p on a Pixel silently recorded at 1080p because
    // stabilization won. Whatever is given up is now reported by onFeaturesSelected().
    //
    // Both stabilization features are listed because the EIS toggle is offered whenever
    // either kind is supported (see canApplyVideoStabilization). They share one feature
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

        val features = arrayListOf<GroupableFeature>()

        featureGroup.videoQualityFeature?.let { features.add(it) }
        features.add(GroupableFeature.PREVIEW_STABILIZATION)
        features.add(GroupableFeatures.VIDEO_STABILIZATION)

        return features
    }
}
