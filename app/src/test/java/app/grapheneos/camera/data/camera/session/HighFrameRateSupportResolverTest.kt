package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraInfo
import androidx.camera.core.SessionConfig
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.GroupableFeatures
import app.grapheneos.camera.data.camera.mapper.CameraXConstantsMapperImpl
import app.grapheneos.camera.data.camera.model.HighFrameRateLimit
import app.grapheneos.camera.data.camera.model.HighFrameRateProbeKey
import app.grapheneos.camera.data.camera.model.HighFrameRateSupport
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.LensFacing
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HighFrameRateSupportResolverTest {

    private val resolver = HighFrameRateSupportResolverImpl(
        cameraSessionPlanFactory = CameraSessionPlanFactoryImpl(CameraXConstantsMapperImpl()),
    )

    private val provider = mockk<ProcessCameraProvider>()

    private val probed = mutableListOf<Set<GroupableFeature>>()

    private var supported = emptySet<Set<GroupableFeature>>()

    private val cameraInfo = mockk<CameraInfo> {
        every { isSessionConfigSupported(any()) } answers {
            val features = firstArg<SessionConfig>().requiredFeatureGroup
            probed += features
            features in supported
        }
    }

    @Test
    fun aHighFrameRate_thatFitsTheQualityAndHdr_isSupported() {
        supported = setOf(UHD_HDR_AT_HIGH_FRAME_RATE)

        assertEquals(HighFrameRateSupport.Supported, resolve(key = UHD_HDR))
        assertEquals(listOf(UHD_HDR_AT_HIGH_FRAME_RATE), probed)
    }

    @Test
    fun aHighFrameRate_thatFitsOnlyWithoutHdr_isLimitedByHdr() {
        supported = setOf(UHD_AT_HIGH_FRAME_RATE)

        assertEquals(
            HighFrameRateSupport.Unavailable(limit = HighFrameRateLimit.HDR),
            resolve(key = UHD_HDR),
        )
    }

    @Test
    fun aHighFrameRate_thatDoesNotFitTheQuality_isLimitedByTheResolution() {
        val support = resolve(key = UHD_HDR.copy(hdrVideo = false))

        assertEquals(
            HighFrameRateSupport.Unavailable(limit = HighFrameRateLimit.RESOLUTION),
            support,
        )
        assertEquals(listOf(UHD_AT_HIGH_FRAME_RATE), probed)
    }

    @Test
    fun answers_areRememberedUntilTheCameraStackChanges() {
        supported = setOf(UHD_HDR_AT_HIGH_FRAME_RATE)

        resolve(key = UHD_HDR)
        resolve(key = UHD_HDR)
        resolve(key = UHD_HDR, provider = mockk())

        assertEquals(listOf(UHD_HDR_AT_HIGH_FRAME_RATE, UHD_HDR_AT_HIGH_FRAME_RATE), probed)
    }

    private fun resolve(
        key: HighFrameRateProbeKey,
        provider: ProcessCameraProvider = this.provider,
    ): HighFrameRateSupport {
        return resolver.resolve(
            provider = provider,
            cameraInfo = cameraInfo,
            key = key,
            aspectRatio = AspectRatio.RATIO_16_9,
        )
    }

    private companion object {
        val UHD_HDR = HighFrameRateProbeKey(
            lensFacing = LensFacing.BACK,
            videoQualityFeature = GroupableFeatures.UHD_RECORDING,
            hdrVideo = true,
        )

        val UHD_AT_HIGH_FRAME_RATE = setOf(
            GroupableFeatures.UHD_RECORDING,
            GroupableFeature.FPS_60,
        )
        val UHD_HDR_AT_HIGH_FRAME_RATE = UHD_AT_HIGH_FRAME_RATE + GroupableFeature.HDR_HLG10
    }
}
