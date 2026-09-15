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
import app.grapheneos.camera.analyzer.QRAnalyzer
import app.grapheneos.camera.data.camera.session.CameraSessionEnvironment
import app.grapheneos.camera.data.camera.session.QrCodeAnalyzer
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ktx.applyPreviewRatio
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.showStorageLocationNotFoundDialog
import app.grapheneos.camera.ui.videoQualityTitle
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
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

    fun handle(effect: Effect) {
        when (effect) {
            is Effect.ShowMessage -> showMessage(effect.message)
            is Effect.ShowVideoQualityUnsupported -> showVideoQualityUnsupported(effect.quality)
            is Effect.ShowStorageLocationNotFound -> showStorageLocationNotFound()
            is Effect.FlashPreview -> flashPreview(effect.selfIlluminate)
            is Effect.GoToModeTab -> goToModeTab(effect.mode)
            is Effect.ShowZoomPanel -> showZoomPanel()
            is Effect.HideZoomPanel -> hideZoomPanel()
            is Effect.ApplySelfIllumination -> applySelfIllumination(effect.enabled)
            is Effect.ResetTorchToggle -> resetTorchToggle()
            is Effect.ReloadVideoQualities -> reloadVideoQualities()
            is Effect.StartLocationUpdates -> startLocationUpdates()
            is Effect.StopLocationUpdates -> stopLocationUpdates()
        }
    }

    fun render(state: ViewfinderUiState) {
        activity.qrOverlay.visibility = visibleOrInvisible(state.qrOverlayVisible)
        activity.thirdOption.visibility = visibleOrInvisible(state.thirdOptionVisible)
        activity.cancelButtonView.visibility = visibleOrInvisible(state.cancelButtonVisible)

        activity.qrScanToggles.visibility = visibleOrGone(state.qrScanTogglesVisible)
        activity.micOffIcon.visibility = visibleOrGone(state.micMutedIconVisible)

        activity.captureButton.setBackgroundResource(state.captureButtonBackground)
        activity.setCaptureButtonIcon(
            icon = state.captureButtonIcon,
            description = state.captureButtonDescription,
        )
        activity.setFlipCameraIcon(
            icon = state.flipCameraIcon,
            description = state.flipCameraDescription,
        )

        activity.previewGrid.gridType = state.gridType
        activity.cbText.text = state.selfTimerBadge
        activity.cbText.visibility = visibleOrInvisible(state.selfTimerBadgeVisible)

        activity.settingsDialog.render(state.settingsSheet)
    }

    private fun showMessage(@StringRes message: Int) {
        activity.showMessage(message)
    }

    private fun showVideoQualityUnsupported(quality: Quality) {
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

    private fun startLocationUpdates() {
        activity.onRequireLocationChanged(required = true)
    }

    private fun stopLocationUpdates() {
        activity.onRequireLocationChanged(required = false)
    }

    override fun shouldAskForLocationPermission(): Boolean {
        return (activity.applicationContext as App).shouldAskForLocationPermission()
    }

    override fun createQrAnalyzer(): QrCodeAnalyzer {
        return QRAnalyzer(
            mActivity = activity,
            scanAllCodes = { activity.viewfinder.uiState.value.scanAllCodes },
        )
    }

    private fun showStorageLocationNotFound() {
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

    override fun updateZoomThumb() {
        activity.zoomBar.updateThumb()
    }

    private fun showZoomPanel() {
        activity.zoomBar.showPanel()
    }

    private fun hideZoomPanel() {
        activity.zoomBar.hidePanel()
    }

    private fun visibleOrInvisible(visible: Boolean): Int {
        return when {
            visible -> View.VISIBLE
            else -> View.INVISIBLE
        }
    }

    private fun visibleOrGone(visible: Boolean): Int {
        return when {
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

    private fun goToModeTab(mode: CameraMode) {
        activity.tabLayout.getTabForMode(mode)?.let { tab ->
            activity.tabLayout.goToTab(tab)
        }
    }

    private fun reloadVideoQualities() {
        activity.settingsDialog.reloadQualities()
    }

    private fun applySelfIllumination(enabled: Boolean) {
        activity.settingsDialog.selfIllumination(enabled)
    }

    private fun resetTorchToggle() {
        activity.settingsDialog.torchToggle.isChecked = false
    }

    private fun flashPreview(selfIlluminate: Boolean) {
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
