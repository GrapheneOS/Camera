package app.grapheneos.camera.ui.viewfinder.screen

import androidx.annotation.StringRes
import androidx.camera.video.Quality

interface ViewfinderEffects {

    fun showMessage(@StringRes message: Int)

    fun showVideoQualityUnsupported(quality: Quality)

    fun showStorageLocationNotFound()

    fun flashPreview(selfIlluminate: Boolean)

    fun updateLastFrame()

    fun cancelPendingCapture()

    fun forceUpdateOrientationSensor()

    fun startFocusTimer()

    fun cancelFocusTimer()
}
