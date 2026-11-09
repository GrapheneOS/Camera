package app.grapheneos.camera.domain.camera.mapper

import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.core.model.LensFacing
import javax.inject.Inject

internal interface ZoomPresetsMapper {

    fun map(
        zoom: CameraZoom,
        lensFacing: LensFacing,
    ): List<Float>
}

internal class ZoomPresetsMapperImpl @Inject constructor() : ZoomPresetsMapper {

    override fun map(
        zoom: CameraZoom,
        lensFacing: LensFacing,
    ): List<Float> {
        val presets = when (lensFacing) {
            LensFacing.FRONT -> STANDARD_PRESETS
            LensFacing.BACK -> listOf(zoom.minZoomRatio) + STANDARD_PRESETS
        }
        return presets
            .filter { it in zoom.minZoomRatio..zoom.maxZoomRatio }
            .distinct()
            .ifEmpty { listOf(zoom.minZoomRatio) }
    }

    private companion object {
        private val STANDARD_PRESETS = listOf(1f, 2f)
    }
}
