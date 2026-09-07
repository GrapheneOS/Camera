package app.grapheneos.camera.ui.viewfinder

import androidx.annotation.StringRes
import androidx.camera.core.CameraInfo
import androidx.camera.core.ExposureState
import app.grapheneos.camera.data.core.model.CameraMode
import com.google.zxing.BarcodeFormat

interface ViewfinderEffects {

    fun showMessage(@StringRes message: Int)

    fun showMessage(message: String)

    fun applyModeChrome(mode: CameraMode, isVideoMode: Boolean, scanAllCodes: Boolean)

    fun applyScanAllCodesChrome(scanAllCodes: Boolean)

    fun setCameraModeTabs(modes: Set<CameraMode>, currentMode: CameraMode)

    fun goToModeTab(mode: CameraMode)

    fun onPreviewBound(aspectRatio: Int, cameraInfo: CameraInfo)

    fun updateLastFrame()

    fun flashPreview(selfIlluminate: Boolean)

    fun updateZoomThumb(shouldShowPanel: Boolean)

    fun applyExposureState(exposureState: ExposureState)

    fun hideExposurePanel()

    fun setMicMutedIconVisible(visible: Boolean)

    fun updateGyroscopeIndicator(inPhotoMode: Boolean)

    fun forceUpdateOrientationSensor()

    fun startFocusTimer()

    fun cancelFocusTimer()

    fun cancelPendingCapture()

    fun selectBarcodeFormatToggles(formats: List<BarcodeFormat>)

    fun onFlashModeChanged()

    fun onIncludeAudioChanged(enabled: Boolean)

    fun onGeoTaggingChanged(enabled: Boolean)

    fun onSelfIlluminationChanged(enabled: Boolean)

    fun locationCamConfigChanged(required: Boolean)

    fun reloadVideoQualities()

    fun showOnlyRelevantSettings()

    fun resetTorchToggle()

    fun showBarcodeFormatPicker(
        optionNames: List<String>,
        initialValues: List<Boolean>,
        onConfirm: (List<Boolean>) -> Unit,
    )

    fun showStorageLocationNotFound()
}
