package app.grapheneos.camera.ui.viewfinder.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.ktx.applyPreviewRatio
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.showPictureFailureDialog
import app.grapheneos.camera.ui.showStorageLocationNotFoundDialog
import app.grapheneos.camera.ui.videoQualityTitle
import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState

internal class ViewfinderEffectHandler(
    private val activity: MainActivity,
    private val clipboardManager: ClipboardManager,
    private val notificationManager: NotificationManager,
) : ViewfinderChrome {

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
            is Effect.Panel -> handlePanel(effect)
            is Effect.ApplySelfIllumination -> applySelfIllumination(effect.enabled)
            is Effect.SetLocationUpdates -> setLocationUpdates(effect.enabled)
            is Effect.SelfTimer -> handleSelfTimer(effect)
            is Effect.Picture -> handlePicture(effect)
            is Effect.Recording -> handleRecording(effect)
        }
    }

    private fun handlePanel(effect: Effect.Panel) {
        when (effect) {
            is Effect.Panel.ShowZoom -> activity.zoomBar.showPanel()
            is Effect.Panel.HideZoom -> activity.zoomBar.hidePanel()
            is Effect.Panel.HideExposure -> activity.exposureBar.hidePanel()
        }
    }

    private fun handleRecording(effect: Effect.Recording) {
        when (effect) {
            is Effect.Recording.PlayStopSound -> activity.tunePlayer.playVRStopSound()
            is Effect.Recording.Stopped -> activity.forceUpdateOrientationSensor()
            is Effect.Recording.Saved -> onRecordingSaved(effect)

            is Effect.Recording.PlayStartSound -> {
                activity.tunePlayer.playVRStartSound {
                    activity.viewfinder.onAction(RecordingAction.StartSoundPlayed)
                }
            }

            is Effect.Recording.RequestAudioPermission -> {
                activity.restartRecordingWithMicPermission()
            }

            is Effect.Recording.SaveFailed -> {
                activity.showMessage(
                    activity.getString(R.string.unable_to_save_video_verbose, effect.errorCode),
                )
            }

            is Effect.Recording.Interrupted -> {
                activity.showMessage(
                    activity.getString(R.string.error_during_recording, effect.errorCode),
                )
            }
        }
    }

    private fun onRecordingSaved(effect: Effect.Recording.Saved) {
        effect.item?.let { item ->
            activity.capturedItemSession.recordCapturedItem(item)
            activity.updateThumbnail()

            if (activity is SecureMainActivity) {
                activity.capturedItems.add(item)
            }
        }

        if (activity is VideoCaptureActivity) {
            activity.afterRecording(effect.uri)
        }
    }

    private fun handlePicture(effect: Effect.Picture) {
        when (effect) {
            is Effect.Picture.Captured -> activity.tunePlayer.playShutterSound()
            is Effect.Picture.PreviewCaptured -> showCapturedPreview(effect.bitmap)
            is Effect.Picture.PreviewFailed -> showCapturedPreviewFailure()
            is Effect.Picture.PreviewReturned -> captureActivity()?.returnCapturedBitmap()
            is Effect.Picture.PreviewStored -> finishCapture(stored = true)
            is Effect.Picture.PreviewStoreFailed -> finishCapture(stored = false)
            is Effect.Picture.Saved -> onPictureSaved(effect.item)
            is Effect.Picture.CaptureFailed -> showCaptureFailure(effect)
            is Effect.Picture.SaveFailed -> showSaveFailure(effect)

            is Effect.Picture.ThumbnailReady -> {
                activity.imagePreview.setImageBitmap(effect.thumbnail)
            }
        }
    }

    private fun showCapturedPreview(bitmap: Bitmap) {
        val captureActivity = captureActivity() ?: return

        captureActivity.bitmap = bitmap
        captureActivity.showPreview()
    }

    private fun finishCapture(stored: Boolean) {
        if (!stored) {
            activity.showMessage(R.string.unable_to_save_image)
        }

        captureActivity()?.finishWithResult(stored)
    }

    private fun captureActivity(): CaptureActivity? {
        return activity as? CaptureActivity
    }

    private fun showCapturedPreviewFailure() {
        activity.showMessage(R.string.unable_to_capture_image)
        activity.finishActivity(AppCompatActivity.RESULT_CANCELED)
    }

    private fun onPictureSaved(item: CapturedItem) {
        activity.capturedItemSession.recordCapturedItem(item)

        if (activity is SecureMainActivity) {
            activity.capturedItems.add(item)
        }
    }

    private fun showCaptureFailure(effect: Effect.Picture.CaptureFailed) {
        if (!activity.isStarted) return

        showPictureFailureDialog(
            activity = activity,
            message = activity.getString(
                R.string.unable_to_capture_image_verbose,
                effect.errorCode,
            ),
            details = effect.details,
            onCopyDetails = { text ->
                copyFailureDetails(effect.details.name, text)
            },
        )
    }

    private fun showSaveFailure(effect: Effect.Picture.SaveFailed) {
        if (!activity.isStarted) {
            notifySaveFailure()
            return
        }

        when {
            effect.alreadyReported -> activity.showMessage(R.string.unable_to_save_image)

            else -> showPictureFailureDialog(
                activity = activity,
                message = activity.getString(
                    R.string.unable_to_save_image_verbose,
                    effect.stage,
                ),
                details = effect.details,
                onCopyDetails = { text ->
                    copyFailureDetails(effect.details.name, text)
                },
            )
        }
    }

    private fun copyFailureDetails(label: String, text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
        activity.showMessage(R.string.copied_text_to_clipboard)
    }

    private fun notifySaveFailure() {
        val title = activity.getString(R.string.unable_to_save_image)
        val channel = NotificationChannel(
            SAVE_FAILURE_CHANNEL_ID,
            title,
            NotificationManager.IMPORTANCE_HIGH,
        )

        val notification = Notification.Builder(activity, SAVE_FAILURE_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.info)
            setContentTitle(title)
        }.build()

        notificationManager.createNotificationChannel(channel)
        notificationManager.notify(SAVE_FAILURE_NOTIFICATION_ID, notification)
    }

    private fun handleSelfTimer(effect: Effect.SelfTimer) {
        when (effect) {
            is Effect.SelfTimer.Started -> activity.cdTimer.onTimerStarted()
            is Effect.SelfTimer.Ticked -> activity.cdTimer.onTick(effect.secondsLeft)
            is Effect.SelfTimer.Cancelled -> activity.cdTimer.onTimerEnded()

            is Effect.SelfTimer.Finished -> {
                activity.cdTimer.onTimerEnded()
                activity.takePicture()
            }
        }
    }

    fun render(state: ViewfinderUiState) {
        activity.qrOverlay.visibility = visibleOrInvisible(state.qrOverlayVisible)
        activity.thirdOption.visibility = visibleOrInvisible(state.thirdOptionVisible)
        activity.cancelButtonView.visibility = visibleOrInvisible(state.cancelButtonVisible)

        activity.qrScanToggles.visibility = visibleOrGone(state.qrScanTogglesVisible)
        activity.micOffIcon.visibility = visibleOrGone(state.micMutedIconVisible)
        activity.muteToggle.visibility = visibleOrGone(state.muteToggleVisible)
        activity.setMuteToggleState(
            muted = state.isRecordingMuted,
        )
        activity.setThirdCircleIcon(
            icon = state.thirdCircleIcon,
            description = state.thirdCircleDescription,
        )
        activity.timerView.visibility = visibleOrGone(state.recordingTimerVisible)
        activity.timerView.text = state.recordingTimerText
        activity.tabLayout.visibility = visibleOrInvisible(state.modeTabsVisible)
        activity.previewView.keepScreenOn = state.keepScreenOn

        activity.captureButton.render(state.captureButton)
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

    private fun setLocationUpdates(enabled: Boolean) {
        activity.onRequireLocationChanged(required = enabled)
    }

    private fun showStorageLocationNotFound() {
        showStorageLocationNotFoundDialog(activity)
    }

    override fun foreignOutputUri(): Uri? {
        return (activity as? VideoCaptureActivity)
            ?.takeIf { it.isOutputUriAvailable() }
            ?.outputUri
    }

    override fun thumbnailSize(): ThumbnailSize {
        return ThumbnailSize(
            width = activity.imagePreview.width,
            height = activity.imagePreview.height,
        )
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
        private const val SAVE_FAILURE_CHANNEL_ID = "image_saver_error"
        private const val SAVE_FAILURE_NOTIFICATION_ID = 1
    }
}
