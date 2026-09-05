package app.grapheneos.camera.domain.camera.usecase

import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.core.SessionConfig
import androidx.camera.core.UseCase
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.GroupableFeatures
import androidx.camera.video.Quality
import app.grapheneos.camera.domain.camera.model.CameraBindRequest
import app.grapheneos.camera.domain.camera.model.CameraSessionPlan
import app.grapheneos.camera.domain.camera.model.FeatureGroupRequest
import app.grapheneos.camera.domain.camera.model.ImageCaptureMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BuildCameraSessionPlanImplTest {

    @Test
    fun invoke_photoMode_buildsAPhotoAndPreviewSession() {
        val plan = buildPlan(request())

        assertNotNull(plan.imageCapture)
        assertNull(plan.videoCapture)
        assertTrue(plan.preferredFeatures.isEmpty())
    }

    @Test
    fun invoke_videoModeWithoutPhotoCapture_buildsNoImageCapture() {
        val plan = buildPlan(
            request(includesVideoCapture = true, includesImageCapture = false),
        )

        assertNotNull(plan.videoCapture)
        assertNull(plan.imageCapture)
    }

    @Test
    fun invoke_focusLockWinsOverZeroShutterLag() {
        val plan = buildPlan(request(waitForFocusLock = true, enableZsl = true))

        assertEquals(ImageCaptureMode.MAXIMIZE_QUALITY, plan.captureMode)
        assertEquals(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY, plan.imageCapture?.captureMode)
    }

    @Test
    fun invoke_zeroShutterLagAlone_isRequested() {
        val plan = buildPlan(request(enableZsl = true))

        assertEquals(ImageCaptureMode.ZERO_SHUTTER_LAG, plan.captureMode)
        assertEquals(ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG, plan.imageCapture?.captureMode)
    }

    @Test
    fun invoke_neitherFocusLockNorZeroShutterLag_minimizesLatency() {
        val plan = buildPlan(request())

        assertEquals(ImageCaptureMode.MINIMIZE_LATENCY, plan.captureMode)
        assertEquals(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY, plan.imageCapture?.captureMode)
    }

    @Test
    fun invoke_theRequestedFlashAndRotation_reachTheImageCapture() {
        val plan = buildPlan(
            request(flashMode = ImageCapture.FLASH_MODE_ON, imageCaptureTargetRotation = 3),
        )

        assertEquals(ImageCapture.FLASH_MODE_ON, plan.imageCapture?.flashMode)
        assertEquals(3, plan.imageCapture?.targetRotation)
    }

    @Test
    fun invoke_aFeatureGroupSession_leadsWithTheVideoQuality() {
        val plan = buildPlan(featureGroupRequest())

        assertEquals(
            listOf(
                GroupableFeatures.UHD_RECORDING,
                GroupableFeature.PREVIEW_STABILIZATION,
                GroupableFeatures.VIDEO_STABILIZATION,
            ),
            plan.preferredFeatures,
        )
    }

    @Test
    fun invoke_aFeatureGroupSessionWithoutAMappableQuality_asksOnlyForStabilization() {
        val plan = buildPlan(featureGroupRequest(videoQualityFeature = null))

        assertEquals(
            listOf(
                GroupableFeature.PREVIEW_STABILIZATION,
                GroupableFeatures.VIDEO_STABILIZATION,
            ),
            plan.preferredFeatures,
        )
    }

    // CameraX 1.6.0's SessionConfig rejects at construction time any use case that configured a
    // groupable feature through a non-groupable API while a feature group is in use --
    // Recorder.Builder.setQualitySelector() and Preview.Builder.setPreviewStabilizationEnabled()
    // are both such APIs. Constructing the SessionConfig the bind would construct is what proves
    // the plan kept away from them.
    @Test
    fun invoke_aFeatureGroupSession_constructsASessionConfigWithoutThrowing() {
        val plan = buildPlan(featureGroupRequest())

        val sessionConfig = SessionConfig(
            useCases = plan.useCases(),
            preferredFeatureGroup = plan.preferredFeatures,
        )

        assertEquals(3, sessionConfig.useCases.size)
    }

    @Test
    fun invoke_aPlainSession_constructsASessionConfigWithoutThrowing() {
        val plan = buildPlan(request(includesVideoCapture = true, includesImageCapture = true))

        val sessionConfig = SessionConfig(useCases = plan.useCases())

        assertEquals(3, sessionConfig.useCases.size)
    }

    private fun buildPlan(request: CameraBindRequest): CameraSessionPlan {
        return BuildCameraSessionPlanImpl().invoke(request)
    }

    private fun CameraSessionPlan.useCases(): List<UseCase> {
        return listOfNotNull(videoCapture, imageCapture, preview)
    }

    private fun featureGroupRequest(
        videoQualityFeature: GroupableFeature? = GroupableFeatures.UHD_RECORDING,
    ): CameraBindRequest {
        return request(
            includesVideoCapture = true,
            includesImageCapture = true,
            featureGroup = FeatureGroupRequest.Requested(videoQualityFeature),
        )
    }

    private fun request(
        includesVideoCapture: Boolean = false,
        includesImageCapture: Boolean = true,
        flashMode: Int = ImageCapture.FLASH_MODE_OFF,
        imageCaptureTargetRotation: Int = 0,
        waitForFocusLock: Boolean = false,
        enableZsl: Boolean = false,
        featureGroup: FeatureGroupRequest = FeatureGroupRequest.Unused,
    ): CameraBindRequest {
        return CameraBindRequest(
            includesVideoCapture = includesVideoCapture,
            includesImageCapture = includesImageCapture,
            aspectRatio = AspectRatio.RATIO_16_9,
            imageCaptureTargetRotation = imageCaptureTargetRotation,
            previewTargetRotation = 0,
            flashMode = flashMode,
            photoQuality = 90,
            waitForFocusLock = waitForFocusLock,
            enableZsl = enableZsl,
            selectHighestResolution = false,
            videoQuality = Quality.UHD,
            mirrorVideoOnFrontCamera = false,
            featureGroup = featureGroup,
        )
    }
}
