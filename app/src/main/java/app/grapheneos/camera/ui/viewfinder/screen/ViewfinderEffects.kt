package app.grapheneos.camera.ui.viewfinder.screen

import androidx.annotation.StringRes
import androidx.camera.video.Quality
import app.grapheneos.camera.data.core.model.CameraMode

interface ViewfinderEffects {

    fun showMessage(@StringRes message: Int)

    fun showVideoQualityUnsupported(quality: Quality)

    fun showStorageLocationNotFound()

    fun flashPreview(selfIlluminate: Boolean)

    fun updateLastFrame()

    fun cancelPendingCapture()

    fun forceUpdateOrientationSensor()

    fun goToModeTab(mode: CameraMode)

    fun showZoomPanel()

    fun hideZoomPanel()

    fun hideExposurePanel()

    fun resetTorchToggle()

    fun reloadVideoQualities()

    fun startLocationUpdates()

    fun stopLocationUpdates()

    fun startFocusTimer()

    fun cancelFocusTimer()
}
