package app.grapheneos.camera.domain.camera.usecase

import androidx.camera.core.CameraSelector
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.GroupableFeatures
import androidx.camera.video.Quality
import app.grapheneos.camera.domain.camera.mapper.VideoQualityFeatureMapperImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResolveDroppedVideoQualityImplTest {

    private val resolve = ResolveDroppedVideoQualityImpl(VideoQualityFeatureMapperImpl())

    private fun droppedQuality(
        lensFacing: Int = BACK,
        requestedQualityFeature: GroupableFeature? = QUALITY,
        selected: Set<GroupableFeature> = emptySet(),
    ): Quality? {
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
        assertEquals(Quality.UHD, droppedQuality())
    }

    @Test
    fun invoke_aFeatureThatNamesNoQualityWasDropped_reportsNothing() {
        assertNull(droppedQuality(requestedQualityFeature = GroupableFeature.PREVIEW_STABILIZATION))
    }

    @Test
    fun invoke_theSameLossTwiceOnOneCamera_reportsItOnce() {
        assertEquals(Quality.UHD, droppedQuality())
        assertNull(droppedQuality())
    }

    @Test
    fun invoke_theSameLossOnBothCameras_reportsItPerCamera() {
        assertEquals(Quality.UHD, droppedQuality(lensFacing = BACK))
        assertEquals(Quality.UHD, droppedQuality(lensFacing = FRONT))
    }

    @Test
    fun invoke_aSatisfiedBindOnTheOtherCamera_leavesThisCamerasLossRemembered() {
        droppedQuality(lensFacing = BACK)
        droppedQuality(lensFacing = FRONT, selected = setOf(QUALITY))

        assertNull(droppedQuality(lensFacing = BACK))
    }

    @Test
    fun invoke_aSatisfiedBindOnThisCamera_makesTheNextLossNewsAgain() {
        droppedQuality()
        droppedQuality(selected = setOf(QUALITY))

        assertEquals(Quality.UHD, droppedQuality())
    }

    @Test
    fun invoke_aDifferentQualityIsDropped_reportsItEvenOnTheSameCamera() {
        assertEquals(Quality.UHD, droppedQuality())

        assertEquals(
            Quality.FHD,
            droppedQuality(requestedQualityFeature = GroupableFeatures.FHD_RECORDING),
        )
    }

    private companion object {
        const val BACK = CameraSelector.LENS_FACING_BACK
        const val FRONT = CameraSelector.LENS_FACING_FRONT

        val QUALITY: GroupableFeature = GroupableFeatures.UHD_RECORDING
    }
}
