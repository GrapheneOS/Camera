package app.grapheneos.camera.ui.viewfinder.screen

import android.content.Context
import android.os.Build
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import androidx.annotation.StringRes
import androidx.camera.core.CameraInfo
import androidx.camera.core.ExposureState
import androidx.camera.core.Preview
import androidx.camera.video.Quality
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.grapheneos.camera.App
import app.grapheneos.camera.R
import app.grapheneos.camera.TunePlayer
import app.grapheneos.camera.analyzer.QRAnalyzer
import app.grapheneos.camera.data.camera.session.CameraSessionEnvironment
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ktx.applyPreviewRatio
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.showStorageLocationNotFoundDialog
import app.grapheneos.camera.ui.videoQualityTitle
import java.util.concurrent.Executor

internal class ViewfinderEffectHandler(
    private val activity: MainActivity,
) : CameraSessionEnvironment,
    ViewfinderEffects,
    ViewfinderChrome {

    override val sessionContext: Context
        get() {
            return activity
        }

    override val sessionLifecycleOwner: LifecycleOwner
        get() {
            return activity
        }

    override val sessionMainExecutor: Executor
        get() {
            return ContextCompat.getMainExecutor(activity)
        }

    override val isSessionActive: Boolean
        get() {
            return !activity.isDestroyed && !activity.isFinishing
        }

    override val previewSurfaceProvider: Preview.SurfaceProvider
        get() {
            return activity.previewView.surfaceProvider
        }

    override val displayRotation: Int
        get() {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    activity.display?.rotation ?: deprecatedDisplayRotation()
                }

                // We don't really have any option here, but this initialization ensures that the
                // app doesn't break later when the below deprecated option gets removed post
                // Android R
                else -> deprecatedDisplayRotation()
            }
        }

    override fun showMessage(@StringRes message: Int) {
        activity.showMessage(message)
    }

    override fun showVideoQualityUnsupported(quality: Quality) {
        activity.showMessage(
            activity.getString(
                R.string.quality_unsupported,
                videoQualityTitle(activity, quality),
            )
        )
    }

    override fun updateLastFrame() {
        activity.updateLastFrame()
    }

    override fun forceUpdateOrientationSensor() {
        activity.forceUpdateOrientationSensor()
    }

    override fun startFocusTimer() {
        activity.startFocusTimer()
    }

    override fun cancelFocusTimer() {
        activity.cancelFocusTimer()
    }

    override fun onRequireLocationChanged(required: Boolean) {
        activity.onRequireLocationChanged(required)
    }

    override fun shouldAskForLocationPermission(): Boolean {
        return (activity.applicationContext as App).shouldAskForLocationPermission()
    }

    override fun createTunePlayer(): TunePlayer {
        return TunePlayer(activity)
    }

    override fun createQrAnalyzer(): QRAnalyzer {
        return QRAnalyzer(activity)
    }

    override fun showStorageLocationNotFound() {
        showStorageLocationNotFoundDialog(activity)
    }

    override fun cancelPendingCapture() {
        activity.imageCapturer.cancelPendingCaptureRequest()
    }

    override fun hideExposurePanel() {
        activity.exposureBar.hidePanel()
    }

    override fun applyExposureState(exposureState: ExposureState) {
        activity.exposureBar.setExposureConfig(exposureState)
    }

    override fun updateZoomThumb(shouldShowPanel: Boolean) {
        activity.zoomBar.updateThumb(shouldShowPanel)
    }

    override fun setMicMutedIconVisible(visible: Boolean) {
        activity.micOffIcon.visibility = when {
            visible -> View.VISIBLE
            else -> View.GONE
        }
    }

    override fun onPreviewBound(aspectRatio: Int, cameraInfo: CameraInfo) {
        // Focus camera on touch/tap
        activity.previewView.setOnTouchListener(activity.gestureHandler)
        activity.previewView.applyPreviewRatio(aspectRatio, cameraInfo)
    }

    override fun updateGyroscopeIndicator(inPhotoMode: Boolean) {
        when {
            inPhotoMode -> activity.sensorNotifier?.forceUpdateGyro()
            else -> activity.gCircleFrame.visibility = View.GONE
        }
    }

    override fun setCameraModeTabs(modes: Set<CameraMode>, currentMode: CameraMode) {
        activity.tabLayout.setModes(
            modes = modes,
            currentMode = currentMode,
            onTabTouched = activity::finalizeMode,
        )
    }

    override fun goToModeTab(mode: CameraMode) {
        activity.tabLayout.getTabForMode(mode)?.let { tab ->
            activity.tabLayout.goToTab(tab)
        }
    }

    override fun onFlashModeChanged() {
        activity.settingsDialog.updateFlashMode()
    }

    override fun onIncludeAudioChanged(enabled: Boolean) {
        activity.settingsDialog.includeAudioToggle.isChecked = enabled
    }

    override fun onGeoTaggingChanged(enabled: Boolean) {
        activity.settingsDialog.locToggle.isChecked = enabled
    }

    override fun onSelfIlluminationChanged(enabled: Boolean) {
        activity.settingsDialog.selfIlluminationToggle.isChecked = enabled
        activity.settingsDialog.selfIllumination()
    }

    override fun reloadVideoQualities() {
        activity.settingsDialog.reloadQualities()
    }

    override fun showOnlyRelevantSettings() {
        activity.settingsDialog.showOnlyRelevantSettings()
    }

    override fun resetTorchToggle() {
        activity.settingsDialog.torchToggle.isChecked = false
    }

    override fun applyModeChrome(
        mode: CameraMode,
        isVideoMode: Boolean,
        scanAllCodes: Boolean,
    ) {
        when (mode) {
            CameraMode.QR_SCAN -> {
                activity.qrOverlay.visibility = View.VISIBLE
                activity.thirdOption.visibility = View.INVISIBLE

                applyScanAllCodesChrome(scanAllCodes)

                activity.cancelButtonView.visibility = View.INVISIBLE

                activity.captureButton.setBackgroundResource(android.R.color.transparent)
                // Entering QR mode always leaves the torch off
                activity.setCaptureButtonIcon(R.drawable.torch_off_button, R.string.turn_torch_on)

                activity.micOffIcon.visibility = View.GONE
            }

            else -> {
                activity.qrOverlay.visibility = View.INVISIBLE
                activity.thirdOption.visibility = View.VISIBLE
                activity.setFlipCameraIcon(R.drawable.flip_camera, R.string.flip_camera)
                activity.cancelButtonView.visibility = View.VISIBLE

                activity.qrScanToggles.visibility = View.GONE

                activity.captureButton.setBackgroundResource(R.drawable.cbutton_bg)

                when {
                    isVideoMode -> {
                        activity.setCaptureButtonIcon(
                            icon = R.drawable.recording,
                            description = R.string.start_recording,
                        )
                    }

                    else -> {
                        activity.setCaptureButtonIcon(
                            icon = R.drawable.camera_shutter,
                            description = R.string.capture,
                        )

                        activity.micOffIcon.visibility = View.GONE
                    }
                }
            }
        }

        activity.updateSelfTimerBadge()
    }

    override fun applyScanAllCodesChrome(scanAllCodes: Boolean) {
        when {
            scanAllCodes -> {
                activity.setFlipCameraIcon(R.drawable.cancel, R.string.stop_scanning_all_formats)
                activity.qrScanToggles.visibility = View.GONE
            }

            else -> {
                activity.setFlipCameraIcon(R.drawable.auto, R.string.scan_all_formats)
                activity.qrScanToggles.visibility = View.VISIBLE
            }
        }
    }

    override fun flashPreview(selfIlluminate: Boolean) {
        val animation: Animation = when {
            selfIlluminate -> AlphaAnimation(0f, 0.8f)
            else -> AlphaAnimation(1f, 0f)
        }

        animation.interpolator = LinearInterpolator()

        when {
            selfIlluminate -> {
                animation.duration = PREVIEW_SL_OVERLAY_DUR
                animation.fillAfter = true
                activity.mainOverlay.setImageResource(android.R.color.white)
            }

            else -> {
                animation.duration = PREVIEW_SNAP_DURATION
                animation.repeatMode = Animation.REVERSE
                activity.mainOverlay.setImageResource(android.R.color.black)
            }
        }

        animation.setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(p0: Animation?) {
                    activity.mainOverlay.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(p0: Animation?) {
                    if (!selfIlluminate) {
                        activity.mainOverlay.visibility = View.INVISIBLE
                        activity.mainOverlay.setImageResource(android.R.color.transparent)
                    }
                }

                override fun onAnimationRepeat(p0: Animation?) {}
            }
        )

        activity.mainOverlay.startAnimation(animation)
    }

    @Suppress("DEPRECATION")
    private fun deprecatedDisplayRotation(): Int {
        return activity.windowManager.defaultDisplay.rotation
    }

    private companion object {
        private const val PREVIEW_SNAP_DURATION = 200L
        private const val PREVIEW_SL_OVERLAY_DUR = 200L
    }
}
