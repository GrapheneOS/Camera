package app.grapheneos.camera.util

import androidx.camera.core.ZoomState
import app.grapheneos.camera.data.camera.session.CameraSession

class CameraControl(private val session: CameraSession) {

    private fun zoomState(): ZoomState? = session.zoomState

    fun zoomIn() = zoomByRatio(1f)

    fun zoomOut() = zoomByRatio(-1f)

    private fun zoomByRatio(zoomValue: Float) {
        val zoomState = zoomState() ?: return
        val currentZoomRatio = zoomState.zoomRatio
        val newZoomRatio = currentZoomRatio + zoomValue

        val zoomTo =
            if (newZoomRatio > zoomState.maxZoomRatio) zoomState.maxZoomRatio
            else if (newZoomRatio < zoomState.minZoomRatio) zoomState.minZoomRatio
            // smoothly transition between wide angle camera to primary one
            else if (currentZoomRatio < 1 && newZoomRatio > 1) 1f
            else newZoomRatio

        session.camera?.cameraControl?.setZoomRatio(zoomTo)
    }

}
