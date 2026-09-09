package app.grapheneos.camera.ui.viewfinder.screen

import androidx.camera.core.CameraInfo
import androidx.camera.core.ExposureState
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState

interface ViewfinderChrome {

    fun render(state: ViewfinderUiState)

    fun setCameraModeTabs(modes: Set<CameraMode>, currentMode: CameraMode)

    fun onPreviewBound(aspectRatio: Int, cameraInfo: CameraInfo)

    fun updateZoomThumb()

    fun applyExposureState(exposureState: ExposureState)

    fun updateGyroscopeIndicator(inPhotoMode: Boolean)

    fun onIncludeAudioChanged(enabled: Boolean)

    fun onGeoTaggingChanged(enabled: Boolean)

    fun onSelfIlluminationChanged(enabled: Boolean)
}
