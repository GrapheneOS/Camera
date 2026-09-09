package app.grapheneos.camera.ui.viewfinder.screen

import androidx.camera.core.CameraInfo
import androidx.camera.core.ExposureState
import app.grapheneos.camera.data.core.model.CameraMode

interface ViewfinderChrome {

    fun setCameraModeTabs(modes: Set<CameraMode>, currentMode: CameraMode)

    fun onPreviewBound(aspectRatio: Int, cameraInfo: CameraInfo)

    fun updateZoomThumb()

    fun applyExposureState(exposureState: ExposureState)

    fun updateGyroscopeIndicator(inPhotoMode: Boolean)
}
