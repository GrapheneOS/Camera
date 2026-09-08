package app.grapheneos.camera.ui.viewfinder.screen

import androidx.camera.core.CameraInfo
import androidx.camera.core.ExposureState
import app.grapheneos.camera.data.core.model.CameraMode

interface ViewfinderChrome {

    fun applyModeChrome(mode: CameraMode, isVideoMode: Boolean, scanAllCodes: Boolean)

    fun applyScanAllCodesChrome(scanAllCodes: Boolean)

    fun setCameraModeTabs(modes: Set<CameraMode>, currentMode: CameraMode)

    fun goToModeTab(mode: CameraMode)

    fun onPreviewBound(aspectRatio: Int, cameraInfo: CameraInfo)

    fun updateZoomThumb(shouldShowPanel: Boolean)

    fun applyExposureState(exposureState: ExposureState)

    fun hideExposurePanel()

    fun setMicMutedIconVisible(visible: Boolean)

    fun updateGyroscopeIndicator(inPhotoMode: Boolean)

    fun onFlashModeChanged()

    fun onIncludeAudioChanged(enabled: Boolean)

    fun onGeoTaggingChanged(enabled: Boolean)

    fun onSelfIlluminationChanged(enabled: Boolean)

    fun onRequireLocationChanged(required: Boolean)

    fun reloadVideoQualities()

    fun showOnlyRelevantSettings()

    fun resetTorchToggle()
}
