package app.grapheneos.camera.domain.camera.usecase

import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.GroupableFeatures
import app.grapheneos.camera.data.camera.mapper.VideoQualityFeatureMapperImpl
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResolveDroppedVideoQualityImplTest {

    private val resolve = ResolveDroppedVideoQualityImpl(VideoQualityFeatureMapperImpl())

    private fun droppedQuality(
        lensFacing: LensFacing = LensFacing.BACK,
        requestedQualityFeature: GroupableFeature? = QUALITY,
        selected: Set<GroupableFeature> = emptySet(),
    ): VideoQuality? {
        return resolve(
            lensFacing = lensFacing,
            requestedQualityFeature = requestedQualityFeature,
            selected = selected,
        )
    }

    @Test
    fun invoke_theQualityWasKept_reportsNothing() {
        assertNull(droppedQuality(selected = setOf(QUALITY)))
    }

    @Test
    fun invoke_noQualityWasAskedFor_reportsNothing() {
        assertNull(droppedQuality(requestedQualityFeature = null))
    }

    @Test
    fun invoke_theQualityWasDropped_reportsIt() {
        assertEquals(VideoQuality.UHD, droppedQuality())
    }

    @Test
    fun invoke_aFeatureThatNamesNoQualityWasDropped_reportsNothing() {
        assertNull(droppedQuality(requestedQualityFeature = GroupableFeature.PREVIEW_STABILIZATION))
    }

    @Test
    fun invoke_theSameLossTwiceOnOneCamera_reportsItOnce() {
        assertEquals(VideoQuality.UHD, droppedQuality())
        assertNull(droppedQuality())
    }

    @Test
    fun invoke_theSameLossOnBothCameras_reportsItPerCamera() {
        assertEquals(VideoQuality.UHD, droppedQuality(lensFacing = LensFacing.BACK))
        assertEquals(VideoQuality.UHD, droppedQuality(lensFacing = LensFacing.FRONT))
    }

    @Test
    fun invoke_aSatisfiedBindOnTheOtherCamera_leavesThisCamerasLossRemembered() {
        droppedQuality(lensFacing = LensFacing.BACK)
        droppedQuality(lensFacing = LensFacing.FRONT, selected = setOf(QUALITY))

        assertNull(droppedQuality(lensFacing = LensFacing.BACK))
    }

    @Test
    fun invoke_aSatisfiedBindOnThisCamera_makesTheNextLossNewsAgain() {
        droppedQuality()
        droppedQuality(selected = setOf(QUALITY))

        assertEquals(VideoQuality.UHD, droppedQuality())
    }

    @Test
    fun invoke_aDifferentQualityIsDropped_reportsItEvenOnTheSameCamera() {
        assertEquals(VideoQuality.UHD, droppedQuality())

        assertEquals(
            VideoQuality.FHD,
            droppedQuality(requestedQualityFeature = GroupableFeatures.FHD_RECORDING),
        )
    }

    private companion object {
        val QUALITY: GroupableFeature = GroupableFeatures.UHD_RECORDING
    }
}
