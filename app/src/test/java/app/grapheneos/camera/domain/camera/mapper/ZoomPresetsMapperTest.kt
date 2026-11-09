package app.grapheneos.camera.domain.camera.mapper

import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.core.model.LensFacing
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomPresetsMapperTest {

    private val mapper = ZoomPresetsMapperImpl()
    private val zoom = CameraZoom(
        zoomRatio = 1f,
        linearZoom = 0f,
        minZoomRatio = 0.5f,
        maxZoomRatio = 8f,
    )

    @Test
    fun backCamera_showsThreePresetsWithoutAddingTheDigitalMaximum() {
        assertEquals(
            listOf(0.5f, 1f, 2f),
            mapper.map(
                zoom = zoom,
                lensFacing = LensFacing.BACK,
            ),
        )
    }

    @Test
    fun backCamera_wideAngleUsesTheActualMinimumInsteadOfAnUnsupportedHalf() {
        val wideAngle = zoom.copy(minZoomRatio = 0.5304938f)

        assertEquals(
            listOf(0.5304938f, 1f, 2f),
            mapper.map(
                zoom = wideAngle,
                lensFacing = LensFacing.BACK,
            ),
        )
    }

    @Test
    fun frontCamera_usesTwoPresets() {
        val front = zoom.copy(
            minZoomRatio = 1f,
            maxZoomRatio = 4f,
        )

        assertEquals(
            listOf(1f, 2f),
            mapper.map(
                zoom = front,
                lensFacing = LensFacing.FRONT,
            ),
        )
    }

    @Test
    fun unsupportedPresets_areLeftOut() {
        val shortRange = zoom.copy(
            minZoomRatio = 1f,
            maxZoomRatio = 1.2f,
        )

        LensFacing.entries.forEach { lens ->
            assertEquals(
                listOf(1f),
                mapper.map(
                    zoom = shortRange,
                    lensFacing = lens,
                ),
            )
        }
    }

    @Test
    fun aRangeOutsideThePresets_keepsItsMinimumSelectable() {
        val unusualRange = zoom.copy(
            minZoomRatio = 3f,
            maxZoomRatio = 4f,
        )

        assertEquals(
            listOf(3f),
            mapper.map(
                zoom = unusualRange,
                lensFacing = LensFacing.BACK,
            ),
        )
    }
}
