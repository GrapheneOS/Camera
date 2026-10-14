package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomDirection
import javax.inject.Inject

interface NextZoomStopMapper {

    fun map(
        zoom: CameraZoom,
        direction: ZoomDirection,
    ): Float?
}

internal class NextZoomStopMapperImpl @Inject constructor() : NextZoomStopMapper {

    override fun map(
        zoom: CameraZoom,
        direction: ZoomDirection,
    ): Float? {
        val stops = (listOf(zoom.minZoomRatio) + MIDDLE_STOPS + zoom.maxZoomRatio)
            .filter { it in zoom.minZoomRatio..zoom.maxZoomRatio }
            .distinct()
            .sorted()

        return when (direction) {
            ZoomDirection.IN -> stops.firstOrNull { it > zoom.zoomRatio }
            ZoomDirection.OUT -> stops.lastOrNull { it < zoom.zoomRatio }
        }
    }

    private companion object {
        val MIDDLE_STOPS = listOf(1f, 2f)
    }
}
