package app.grapheneos.camera.util

import androidx.camera.core.ZoomState
import app.grapheneos.camera.CamConfig

class CameraControl(private val camConfig: CamConfig) {

    private fun zoomState(): ZoomState? = camConfig.camera?.cameraInfo?.zoomState?.value

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

        camConfig.camera?.cameraControl?.setZoomRatio(zoomTo)
    }

}
