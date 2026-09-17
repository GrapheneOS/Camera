package app.grapheneos.camera.data.camera.mapper

import androidx.camera.core.ExposureState
import androidx.camera.core.ZoomState
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import javax.inject.Inject

interface CameraXStateMapper {

    fun map(zoomState: ZoomState): CameraZoom

    fun map(exposureState: ExposureState): CameraExposure
}

internal class CameraXStateMapperImpl @Inject constructor() : CameraXStateMapper {

    override fun map(zoomState: ZoomState): CameraZoom {
        return CameraZoom(
            zoomRatio = zoomState.zoomRatio,
            linearZoom = zoomState.linearZoom,
            minZoomRatio = zoomState.minZoomRatio,
            maxZoomRatio = zoomState.maxZoomRatio,
        )
    }

    override fun map(exposureState: ExposureState): CameraExposure {
        val range = exposureState.exposureCompensationRange

        return CameraExposure(
            compensationIndex = exposureState.exposureCompensationIndex,
            compensationRange = range.lower..range.upper,
        )
    }
}
