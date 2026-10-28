package app.grapheneos.camera.ui.viewfinder.screen

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.di.core.cameraEntryPointArguments
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.camerasettings.CameraSettingsActivity
import app.grapheneos.camera.ui.camerasettings.SecureCameraSettingsActivity
import app.grapheneos.camera.ui.camerasettings.screen.cameraSettingsArguments
import app.grapheneos.camera.ui.common.dialog.showMoreQrFormatOptions
import app.grapheneos.camera.ui.common.dialog.showPictureFailureDialog
import app.grapheneos.camera.ui.common.dialog.showQrResultDialog
import app.grapheneos.camera.ui.common.videoQualityTitle
import app.grapheneos.camera.ui.core.activity.SecureActivity
import app.grapheneos.camera.ui.gallery.GalleryActivity
import app.grapheneos.camera.ui.gallery.SecureGalleryActivity
import app.grapheneos.camera.ui.gallery.screen.galleryArguments
import app.grapheneos.camera.ui.mediaviewer.navigation.shareCapturedItem
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.DialogAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.PermissionAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect

internal interface ViewfinderEffectHandler {
    fun handle(effect: Effect)
}

internal class ViewfinderEffectHandlerImpl(
    private val activity: MainActivity,
    private val vibrator: Vibrator,
    private val onAction: (ViewfinderAction) -> Unit,
) : ViewfinderEffectHandler {

    private val permissionRequests = AppPermission.entries.associateWith { permission ->
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            onAction(PermissionAction.RequestAnswered(permission))
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.ShowMessage -> activity.showMessage(effect.message)
            is Effect.ShowVideoQualityUnsupported -> showVideoQualityUnsupported(effect.quality)
            is Effect.ShowQrResult -> showQrResult(effect.text)
            is Effect.AnimateLensSwitch -> activity.animateLensSwitch()
            is Effect.PlayLevelHaptic -> playLevelHaptic()
            is Effect.CloseScreen -> activity.finish()
            is Effect.Preview -> handlePreview(effect)
            is Effect.Settings -> handleSettings(effect)
            is Effect.ModeTab -> handleModeTab(effect)
            is Effect.Gallery -> handleGallery(effect)
            is Effect.Location -> handleLocation(effect)
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

    private fun showQrResult(text: String) {
        showQrResultDialog(
            activity = activity,
            rawText = text,
            onCopyText = { copiedText -> copy(copiedText.toString()) },
            onDismissed = {
                onAction(LifecycleAction.QrResultDismissed)
            },
        )
    }

    private fun playLevelHaptic() {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    private fun handlePreview(effect: Effect.Preview) {
        when (effect) {
            is Effect.Preview.ShowFocus -> showFocus(effect)
            is Effect.Preview.Flash -> flashPreview(effect.selfIlluminate)
            is Effect.Preview.ApplySelfIllumination -> applySelfIllumination(effect.enabled)
        }
    }

    private fun showFocus(effect: Effect.Preview.ShowFocus) {
        activity.animateFocusRing(effect.x, effect.y)
        activity.exposureBar.showPanel()
        activity.zoomBar.showPanel()
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

    private fun applySelfIllumination(enabled: Boolean) {
        activity.settingsDialog.selfIllumination(enabled)
    }

    private fun handleSettings(effect: Effect.Settings) {
        when (effect) {
            is Effect.Settings.OpenSheet -> openSettingsSheet()
            is Effect.Settings.CloseSheet -> activity.settingsDialog.slideDialogUp()
            is Effect.Settings.ShowQrFormats -> showQrFormats()
            is Effect.Settings.OpenCameraSettings -> openCameraSettings(effect)
        }
    }

    private fun openSettingsSheet() {
        if (activity.settingsDialog.isShowing || !activity.settingsIcon.isEnabled) return

        activity.settingsDialog.show()
    }

    private fun showQrFormats() {
        if (activity.settingsDialog.isShowing) return

        showMoreQrFormatOptions(
            activity = activity,
            barcodeFormats = activity.barcodeFormats,
        )
    }

    private fun openCameraSettings(effect: Effect.Settings.OpenCameraSettings) {
        val screen = when (activity) {
            is SecureActivity -> SecureCameraSettingsActivity::class.java
            else -> CameraSettingsActivity::class.java
        }
        val intent = Intent(activity, screen)
            .putExtras(cameraEntryPointArguments(activity.cameraEntryPoint))
            .putExtras(cameraSettingsArguments(effect.capabilities))

        activity.startActivity(intent)
    }

    private fun handleModeTab(effect: Effect.ModeTab) {
        when (effect) {
            is Effect.ModeTab.SelectAdjacent -> selectAdjacentModeTab(effect.offset)
            is Effect.ModeTab.GoTo -> goToModeTab(effect.mode)
        }
    }

    private fun selectAdjacentModeTab(offset: Int) {
        if (activity.settingsDialog.isShowing) return

        val tabLayout = activity.tabLayout

        tabLayout.getTabAt(tabLayout.selectedTabPosition + offset)?.let(activity::finalizeMode)
    }

    private fun goToModeTab(mode: CameraMode) {
        activity.tabLayout.getTabForMode(mode)?.let { tab ->
            activity.tabLayout.goToTab(tab)
        }
    }

    private fun handleGallery(effect: Effect.Gallery) {
        when (effect) {
            is Effect.Gallery.Open -> openGallery(effect)
            is Effect.Gallery.OpenSecure -> openSecureGallery(effect)
            is Effect.Gallery.Share -> share(effect.item)
        }
    }

    private fun openGallery(effect: Effect.Gallery.Open) {
        startGallery(
            screen = GalleryActivity::class.java,
            arguments = galleryArguments(
                sessionItems = null,
                lastCapturedItem = effect.lastCapturedItem,
            ),
        )
    }

    private fun openSecureGallery(effect: Effect.Gallery.OpenSecure) {
        startGallery(
            screen = SecureGalleryActivity::class.java,
            arguments = galleryArguments(
                sessionItems = effect.capturedItems,
                lastCapturedItem = effect.lastCapturedItem,
            ),
        )
    }

    private fun startGallery(
        screen: Class<out GalleryActivity>,
        arguments: Bundle,
    ) {
        val intent = Intent(activity, screen)
            .putExtras(cameraEntryPointArguments(activity.cameraEntryPoint))
            .putExtras(arguments)

        activity.startActivity(intent)
    }

    private fun share(item: CapturedItem) {
        shareCapturedItem(activity, item)?.let { message ->
            activity.showMessage(message)
        }
    }

    private fun handleLocation(effect: Effect.Location) {
        when (effect) {
            is Effect.Location.ShowDisabled -> showLocationDisabled(effect.offersSettings)
            is Effect.Location.OpenSettings -> openLocationSettings()
        }
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
            is Effect.Picture.CaptureFailed -> showCaptureFailure(effect)
            is Effect.Picture.SaveFailed -> showSaveFailure(effect)
            is Effect.Picture.PreviewCaptured -> showCapturedPreview(effect.bitmap)
            is Effect.Picture.PreviewFailed -> showCapturedPreviewFailure()
            is Effect.Picture.PreviewStored -> finishCapture(stored = true)
            is Effect.Picture.PreviewStoreFailed -> finishCapture(stored = false)

            is Effect.Picture.PreviewReturned -> {
                captureActivity()?.returnCapturedBitmap(effect.bitmap)
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
            stackTrace = effect.stackTrace,
            onCopyDetails = ::copy,
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
                stackTrace = effect.stackTrace,
                onCopyDetails = ::copy,
            )
        }
    }

    private fun copy(text: String) {
        onAction(DialogAction.CopyClicked(text))
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
            is Effect.Recording.ShowForReview -> videoCaptureActivity()?.afterRecording(effect.uri)

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

    private fun handlePermission(effect: Effect.Permission) {
        when (effect) {
            is Effect.Permission.Request -> requestPermission(effect)
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

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", activity.packageName, null))

        activity.startActivity(intent)
    }

    private fun captureActivity(): CaptureActivity? {
        return activity as? CaptureActivity
    }

    private fun videoCaptureActivity(): VideoCaptureActivity? {
        return activity as? VideoCaptureActivity
    }

    private companion object {
        private const val PREVIEW_SNAP_DURATION = 200L
        private const val SELF_ILLUMINATION_OVERLAY_DURATION = 200L
        private const val SELF_ILLUMINATION_OVERLAY_ALPHA = 0.8f
    }
}
