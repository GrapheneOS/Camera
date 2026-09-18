package app.grapheneos.camera.ui.viewfinder.screen

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import androidx.annotation.StringRes
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.ktx.applyPreviewRatio
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.showStorageLocationNotFoundDialog
import app.grapheneos.camera.ui.videoQualityTitle
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState

internal class ViewfinderEffectHandler(
    private val activity: MainActivity,
) : ViewfinderChrome {

    private var renderedCaptureButtonIcon: Int? = null
    private var renderedCaptureButtonEnabled = true
    private var renderedThumbnailLoaderVisible = false
    private var renderedModes: Set<CameraMode> = emptySet()
    private var renderedAspectRatio: AspectRatio? = null
    private var renderedSensorOrientationDegrees: Int? = null
    private var renderedInPhotoMode: Boolean? = null

    fun handle(effect: Effect) {
        when (effect) {
            is Effect.ShowMessage -> showMessage(effect.message)
            is Effect.ShowVideoQualityUnsupported -> showVideoQualityUnsupported(effect.quality)
            is Effect.ShowStorageLocationNotFound -> showStorageLocationNotFound()
            is Effect.FlashPreview -> flashPreview(effect.selfIlluminate)
            is Effect.GoToModeTab -> goToModeTab(effect.mode)
            is Effect.ShowQrResult -> activity.showQrResult(effect.text)
            is Effect.ShowZoomPanel -> showZoomPanel()
            is Effect.HideZoomPanel -> hideZoomPanel()
            is Effect.HideExposurePanel -> hideExposurePanel()
            is Effect.ApplySelfIllumination -> applySelfIllumination(effect.enabled)
            is Effect.StartLocationUpdates -> startLocationUpdates()
            is Effect.StopLocationUpdates -> stopLocationUpdates()
            is Effect.SelfTimer -> handleSelfTimer(effect)
        }
    }

    private fun handleSelfTimer(effect: Effect.SelfTimer) {
        when (effect) {
            is Effect.SelfTimer.Started -> activity.cdTimer.onTimerStarted()
            is Effect.SelfTimer.Ticked -> activity.cdTimer.onTick(effect.secondsLeft)
            is Effect.SelfTimer.Finished -> activity.cdTimer.onTimerFinished()
            is Effect.SelfTimer.Cancelled -> activity.cdTimer.onTimerCancelled()
        }
    }

    fun render(state: ViewfinderUiState) {
        activity.qrOverlay.visibility = visibleOrInvisible(state.qrOverlayVisible)
        activity.thirdOption.visibility = visibleOrInvisible(state.thirdOptionVisible)
        activity.cancelButtonView.visibility = visibleOrInvisible(state.cancelButtonVisible)

        activity.qrScanToggles.visibility = visibleOrGone(state.qrScanTogglesVisible)
        activity.micOffIcon.visibility = visibleOrGone(state.micMutedIconVisible)

        activity.captureButton.setBackgroundResource(state.captureButtonBackground)
        activity.setFlipCameraIcon(
            icon = state.flipCameraIcon,
            description = state.flipCameraDescription,
        )

        activity.previewGrid.gridType = state.gridType
        activity.cbText.text = state.selfTimerBadge
        activity.cbText.visibility = visibleOrInvisible(state.selfTimerBadgeVisible)
        activity.cdTimer.visibility = visibleOrGone(state.selfTimerCountdownVisible)
        activity.cbCross.visibility = visibleOrInvisible(state.selfTimerCancelVisible)

        activity.settingsDialog.render(state.settingsSheet)
        activity.zoomBar.render(state.zoom)
        activity.exposureBar.render(state.exposure)

        renderCaptureButton(state)
        renderCaptureButtonEnabled(state.captureButtonEnabled)
        renderThumbnailLoader(state.thumbnailLoaderVisible)
        renderModeTabs(state)
        renderBoundPreview(state)
    }

    private fun renderModeTabs(state: ViewfinderUiState) {
        if (state.availableModes == renderedModes) return
        renderedModes = state.availableModes

        activity.tabLayout.setModes(
            modes = state.availableModes,
            currentMode = state.mode,
            onTabTouched = activity::finalizeMode,
        )
    }

    private fun renderBoundPreview(state: ViewfinderUiState) {
        val sensorOrientationDegrees = state.sensorOrientationDegrees ?: return

        // Focus camera on touch/tap
        activity.previewView.setOnTouchListener(activity.gestureHandler)

        if (state.aspectRatio != renderedAspectRatio ||
            sensorOrientationDegrees != renderedSensorOrientationDegrees
        ) {
            renderedAspectRatio = state.aspectRatio
            renderedSensorOrientationDegrees = sensorOrientationDegrees
            activity.previewView.applyPreviewRatio(
                aspectRatio = state.aspectRatio,
                sensorOrientationDegrees = sensorOrientationDegrees,
            )
        }

        if (state.inPhotoMode != renderedInPhotoMode) {
            renderedInPhotoMode = state.inPhotoMode
            when {
                state.inPhotoMode -> activity.sensorNotifier?.forceUpdateGyro()
                else -> activity.gCircleFrame.visibility = View.GONE
            }
        }
    }

    private fun renderCaptureButton(state: ViewfinderUiState) {
        // The drawable must stay the same one the recording's corner-radius animation is holding
        // on to: replacing it, even with the same resource, would cut that animation short.
        if (state.captureButtonIcon != renderedCaptureButtonIcon) {
            activity.captureButton.setImageResource(state.captureButtonIcon)
            renderedCaptureButtonIcon = state.captureButtonIcon
        }

        activity.captureButton.contentDescription = activity.getString(
            state.captureButtonDescription,
        )
    }

    private fun renderCaptureButtonEnabled(enabled: Boolean) {
        if (enabled == renderedCaptureButtonEnabled) return

        renderedCaptureButtonEnabled = enabled
        activity.captureButton.isEnabled = enabled

        val targetAlpha = when {
            enabled -> CAPTURE_BUTTON_ENABLED_ALPHA
            else -> CAPTURE_BUTTON_DISABLED_ALPHA
        }
        val animation = AlphaAnimation(activity.captureButton.alpha, targetAlpha)
        animation.duration = CAPTURE_BUTTON_FADE_DURATION
        animation.interpolator = LinearInterpolator()
        animation.fillAfter = true

        activity.captureButton.startAnimation(animation)
    }

    private fun renderThumbnailLoader(visible: Boolean) {
        if (visible == renderedThumbnailLoaderVisible) return

        renderedThumbnailLoaderVisible = visible
        activity.previewLoader.visibility = visibleOrGone(visible)
    }

    private fun showMessage(@StringRes message: Int) {
        activity.showMessage(message)
    }

    private fun showVideoQualityUnsupported(quality: VideoQuality) {
        activity.showMessage(
            activity.getString(
                R.string.quality_unsupported,
                videoQualityTitle(activity, quality),
            )
        )
    }

    override fun forceUpdateOrientationSensor() {
        activity.forceUpdateOrientationSensor()
    }

    private fun startLocationUpdates() {
        activity.onRequireLocationChanged(required = true)
    }

    private fun stopLocationUpdates() {
        activity.onRequireLocationChanged(required = false)
    }

    private fun showStorageLocationNotFound() {
        showStorageLocationNotFoundDialog(activity)
    }

    override fun cancelPendingCapture() {
        activity.imageCapturer.cancelPendingCaptureRequest()
    }

    private fun hideExposurePanel() {
        activity.exposureBar.hidePanel()
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

    private fun goToModeTab(mode: CameraMode) {
        activity.tabLayout.getTabForMode(mode)?.let { tab ->
            activity.tabLayout.goToTab(tab)
        }
    }

    private fun applySelfIllumination(enabled: Boolean) {
        activity.settingsDialog.selfIllumination(enabled)
    }

    private fun flashPreview(selfIlluminate: Boolean) {
        val animation: Animation = when {
            selfIlluminate -> AlphaAnimation(SELF_ILLUMINATION_OVERLAY_ALPHA, 0f)
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
                    activity.mainOverlay.visibility = View.INVISIBLE
                    activity.mainOverlay.setImageResource(android.R.color.transparent)
                }

                override fun onAnimationRepeat(p0: Animation?) {}
            }
        )

        activity.mainOverlay.startAnimation(animation)
    }

    private companion object {
        private const val PREVIEW_SNAP_DURATION = 200L
        private const val PREVIEW_SL_OVERLAY_DUR = 200L
        private const val SELF_ILLUMINATION_OVERLAY_ALPHA = 0.8f
        private const val CAPTURE_BUTTON_FADE_DURATION = 200L
        private const val CAPTURE_BUTTON_ENABLED_ALPHA = 1f
        private const val CAPTURE_BUTTON_DISABLED_ALPHA = 0.6f
    }
}
