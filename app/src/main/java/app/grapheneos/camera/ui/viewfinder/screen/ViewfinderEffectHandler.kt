package app.grapheneos.camera.ui.viewfinder.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.showCameraPermissionDialog
import app.grapheneos.camera.ui.showLocationPermissionDialog
import app.grapheneos.camera.ui.showMicrophonePermissionDialog
import app.grapheneos.camera.ui.showPictureFailureDialog
import app.grapheneos.camera.ui.showStorageLocationNotFoundDialog
import app.grapheneos.camera.ui.videoQualityTitle
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.PermissionAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect

internal interface ViewfinderEffectHandler {
    fun handle(effect: Effect)
}

internal class ViewfinderEffectHandlerImpl(
    private val activity: MainActivity,
    private val clipboardManager: ClipboardManager,
    private val vibratorManager: Vibrator,
    private val onAction: (ViewfinderAction) -> Unit,
) : ViewfinderEffectHandler {

    private val permissionRequests = AppPermission.entries.associateWith { permission ->
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            onAction(PermissionAction.RequestAnswered(permission))
        }
    }

    private var permissionDialog: AlertDialog? = null

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.ShowMessage -> activity.showMessage(effect.message)
            is Effect.ShowVideoQualityUnsupported -> showVideoQualityUnsupported(effect.quality)
            is Effect.ShowStorageLocationNotFound -> showStorageLocationNotFoundDialog(activity)
            is Effect.CloseScreen -> activity.finish()
            is Effect.PlayLevelHaptic -> playLevelHaptic()
            is Effect.ShowLocationDisabled -> showLocationDisabled(effect.offersSettings)
            is Effect.OpenLocationSettings -> openLocationSettings()
            is Effect.ShowQrResult -> activity.showQrResult(effect.text)
            is Effect.FlashPreview -> flashPreview(effect.selfIlluminate)
            is Effect.GoToModeTab -> goToModeTab(effect.mode)
            is Effect.ApplySelfIllumination -> applySelfIllumination(effect.enabled)
            is Effect.Panel -> handlePanel(effect)
            is Effect.SelfTimer -> handleSelfTimer(effect)
            is Effect.Picture -> handlePicture(effect)
            is Effect.Recording -> handleRecording(effect)
            is Effect.Permission -> handlePermission(effect)
        }
    }

    private fun showVideoQualityUnsupported(quality: VideoQuality) {
        activity.showMessage(
            activity.getString(
                R.string.quality_unsupported,
                videoQualityTitle(activity, quality),
            ),
        )
    }

    private fun flashPreview(selfIlluminate: Boolean) {
        val animation: Animation = when {
            selfIlluminate -> AlphaAnimation(SELF_ILLUMINATION_OVERLAY_ALPHA, 0f)
            else -> AlphaAnimation(1f, 0f)
        }

        animation.interpolator = LinearInterpolator()

        when {
            selfIlluminate -> {
                animation.duration = SELF_ILLUMINATION_OVERLAY_DURATION
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
                override fun onAnimationStart(animation: Animation?) {
                    activity.mainOverlay.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animation?) {
                    activity.mainOverlay.visibility = View.INVISIBLE
                    activity.mainOverlay.setImageResource(android.R.color.transparent)
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            },
        )

        activity.mainOverlay.startAnimation(animation)
    }

    private fun goToModeTab(mode: CameraMode) {
        activity.tabLayout.getTabForMode(mode)?.let { tab ->
            activity.tabLayout.goToTab(tab)
        }
    }

    private fun applySelfIllumination(enabled: Boolean) {
        activity.settingsDialog.selfIllumination(enabled)
    }

    private fun handlePanel(effect: Effect.Panel) {
        when (effect) {
            is Effect.Panel.ShowZoom -> activity.zoomBar.showPanel()
            is Effect.Panel.HideZoom -> activity.zoomBar.hidePanel()
            is Effect.Panel.HideExposure -> activity.exposureBar.hidePanel()
        }
    }

    private fun handleSelfTimer(effect: Effect.SelfTimer) {
        when (effect) {
            is Effect.SelfTimer.Started -> activity.cdTimer.onTimerStarted()
            is Effect.SelfTimer.Ticked -> activity.cdTimer.onTick(effect.secondsLeft)
            is Effect.SelfTimer.Cancelled -> activity.cdTimer.onTimerEnded()

            is Effect.SelfTimer.Finished -> {
                activity.cdTimer.onTimerEnded()
                onAction(CaptureAction.ShutterClicked)
            }
        }
    }

    private fun handlePicture(effect: Effect.Picture) {
        when (effect) {
            is Effect.Picture.Captured -> activity.tunePlayer.playShutterSound()
            is Effect.Picture.Saved -> recordCapturedItem(effect.item)
            is Effect.Picture.CaptureFailed -> showCaptureFailure(effect)
            is Effect.Picture.SaveFailed -> showSaveFailure(effect)
            is Effect.Picture.PreviewCaptured -> showCapturedPreview(effect.bitmap)
            is Effect.Picture.PreviewFailed -> showCapturedPreviewFailure()
            is Effect.Picture.PreviewReturned -> captureActivity()?.returnCapturedBitmap()
            is Effect.Picture.PreviewStored -> finishCapture(stored = true)
            is Effect.Picture.PreviewStoreFailed -> finishCapture(stored = false)

            is Effect.Picture.ThumbnailReady -> {
                activity.imagePreview.setImageBitmap(effect.thumbnail)
            }
        }
    }

    private fun showCaptureFailure(effect: Effect.Picture.CaptureFailed) {
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

    private fun showCapturedPreview(bitmap: Bitmap) {
        val captureActivity = captureActivity() ?: return

        captureActivity.bitmap = bitmap
        captureActivity.showPreview()
    }

    private fun showCapturedPreviewFailure() {
        activity.showMessage(R.string.unable_to_capture_image)
        activity.finishActivity(AppCompatActivity.RESULT_CANCELED)
    }

    private fun finishCapture(stored: Boolean) {
        if (!stored) {
            activity.showMessage(R.string.unable_to_save_image)
        }

        captureActivity()?.finishWithResult(stored)
    }

    private fun handleRecording(effect: Effect.Recording) {
        when (effect) {
            is Effect.Recording.Saved -> onRecordingSaved(effect)

            is Effect.Recording.PlayStartSound -> {
                activity.tunePlayer.playVRStartSound {
                    onAction(RecordingAction.StartSoundPlayed)
                }
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
            recordCapturedItem(item)
            activity.updateThumbnail()
        }

        if (activity is VideoCaptureActivity) {
            activity.afterRecording(effect.uri)
        }
    }

    private fun recordCapturedItem(item: CapturedItem) {
        activity.capturedItemSession.recordCapturedItem(item)

        if (activity is SecureMainActivity) {
            activity.capturedItems.add(item)
        }
    }

    private fun handlePermission(effect: Effect.Permission) {
        when (effect) {
            is Effect.Permission.Request -> requestPermission(effect)
            is Effect.Permission.ShowDialog -> showPermissionDialog(effect)
            is Effect.Permission.DismissDialog -> permissionDialog?.dismiss()
            is Effect.Permission.OpenSettings -> openAppSettings()
        }
    }

    private fun requestPermission(effect: Effect.Permission.Request) {
        val permission = effect.permission
        val needsRationale = effect.explainsFirst &&
            permission.manifestNames.all { activity.shouldShowRequestPermissionRationale(it) }

        when {
            needsRationale -> {
                onAction(PermissionAction.RationaleRequired(permission))
            }

            else -> {
                permissionRequests
                    .getValue(permission)
                    .launch(permission.manifestNames.toTypedArray())
            }
        }
    }

    private fun showPermissionDialog(effect: Effect.Permission.ShowDialog) {
        val permission = effect.permission
        val onSettingsClicked = { onAction(PermissionAction.SettingsClicked) }
            .takeIf { effect.offersSettings }
        val onDismissed = { onAction(PermissionAction.DialogDismissed(permission)) }

        permissionDialog = when (permission) {
            AppPermission.CAMERA -> showCameraPermissionDialog(
                activity = activity,
                onSettingsClicked = onSettingsClicked,
                onDismissed = onDismissed,
            )

            AppPermission.MICROPHONE -> showMicrophonePermissionDialog(
                activity = activity,
                onSettingsClicked = onSettingsClicked,
                onRecordWithoutAudioClicked = {
                    onAction(RecordingAction.RecordWithoutAudioClicked)
                },
                onDismissed = onDismissed,
            )

            AppPermission.LOCATION -> showLocationPermissionDialog(
                activity = activity,
                onSettingsClicked = onSettingsClicked,
                onDismissed = onDismissed,
            )
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", activity.packageName, null))

        activity.startActivity(intent)
    }

    private fun showLocationDisabled(offersSettings: Boolean) {
        val action = when {
            offersSettings -> activity.getString(R.string.enable)
            else -> null
        }

        activity.showMessage(
            msg = activity.getString(R.string.location_is_disabled),
            action = action,
        ) {
            onAction(SettingsAction.EnableLocationClicked)
        }
    }

    private fun openLocationSettings() {
        activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun playLevelHaptic() {
        vibratorManager.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    private fun captureActivity(): CaptureActivity? {
        return activity as? CaptureActivity
    }

    private companion object {
        private const val PREVIEW_SNAP_DURATION = 200L
        private const val SELF_ILLUMINATION_OVERLAY_DURATION = 200L
        private const val SELF_ILLUMINATION_OVERLAY_ALPHA = 0.8f
    }
}
