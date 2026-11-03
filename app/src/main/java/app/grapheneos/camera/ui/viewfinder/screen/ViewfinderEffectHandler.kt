package app.grapheneos.camera.ui.viewfinder.screen

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.mediaactions.CapturedItemIntents
import app.grapheneos.camera.ui.settingvalues.videoQualityTitle
import app.grapheneos.camera.ui.viewfinder.components.qr.showMoreQrFormatOptions
import app.grapheneos.camera.ui.viewfinder.components.qr.showQrResultDialog
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.DialogAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LocationAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.PermissionAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import kotlinx.coroutines.launch

internal interface ViewfinderEffectHandler {
    fun handle(effect: Effect)
}

internal class ViewfinderEffectHandlerImpl(
    private val activity: MainActivity,
    private val vibrator: Vibrator,
    private val capturedItemIntents: CapturedItemIntents,
    private val onAction: (ViewfinderAction) -> Unit,
) : ViewfinderEffectHandler {

    private val permissionRequests = AppPermission.entries.associateWith { permission ->
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            onAction(PermissionAction.RequestAnswered(permission))
        }
    }

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.ShowMessage -> activity.showMessage(effect.message)
            is Effect.ShowVideoQualityUnsupported -> showVideoQualityUnsupported(effect.quality)
            is Effect.ShowQrResult -> showQrResult(effect.text)
            is Effect.PlayLevelHaptic -> playLevelHaptic()
            is Effect.FlashPreview -> flashPreview(effect.selfIlluminate)
            is Effect.ShowQrFormats -> showQrFormats()
            is Effect.ShareCapturedItem -> share(effect.item)
            is Effect.Location -> handleLocation(effect)
            is Effect.CaptureResult -> handleCaptureResult(effect)
            is Effect.Recording -> handleRecording(effect)
            is Effect.Permission -> handlePermission(effect)
        }
    }

    private fun handleLocation(effect: Effect.Location) {
        when (effect) {
            is Effect.Location.ShowDisabled -> showLocationDisabled(effect.offersSettings)
            is Effect.Location.OpenSettings -> openLocationSettings()
        }
    }

    private fun handleCaptureResult(effect: Effect.CaptureResult) {
        when (effect) {
            is Effect.CaptureResult.PhotoFailed -> abandonCapture()
            is Effect.CaptureResult.PhotoStored -> finishCapture(stored = true)
            is Effect.CaptureResult.PhotoStoreFailed -> finishCapture(stored = false)
            is Effect.CaptureResult.PhotoReturned -> returnPhoto(effect.bitmap)
            is Effect.CaptureResult.VideoReturned -> returnVideo(effect.uri)
        }
    }

    private fun handleRecording(effect: Effect.Recording) {
        when (effect) {
            is Effect.Recording.PlayStartSound -> playStartSound(effect.soundEnabled)
            is Effect.Recording.SaveFailed -> showRecordingSaveFailed(effect.errorCode)
            is Effect.Recording.Interrupted -> showRecordingInterrupted(effect.errorCode)
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
            onCopyText = { copiedText ->
                onAction(DialogAction.QrResultCopyClicked(copiedText.toString()))
            },
            onDismissed = {
                onAction(DialogAction.QrResultDismissed)
            },
        )
    }

    private fun playLevelHaptic() {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
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

    private fun showQrFormats() {
        showMoreQrFormatOptions(
            activity = activity,
            barcodeFormats = activity.barcodeFormats,
        )
    }

    private fun share(item: CapturedItem) {
        capturedItemIntents.share(
            activity = activity,
            item = item,
        )?.let { message ->
            activity.showMessage(message)
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
            onAction(LocationAction.EnableLocationClicked)
        }
    }

    private fun openLocationSettings() {
        activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun abandonCapture() {
        activity.showMessage(R.string.unable_to_capture_image)
        activity.setResult(AppCompatActivity.RESULT_CANCELED)
        activity.finish()
    }

    private fun finishCapture(stored: Boolean) {
        if (!stored) {
            activity.showMessage(R.string.unable_to_save_image)
        }

        val result = when {
            stored -> AppCompatActivity.RESULT_OK
            else -> AppCompatActivity.RESULT_CANCELED
        }

        activity.setResult(result)
        activity.finish()
    }

    private fun returnPhoto(photo: Bitmap) {
        val result = Intent(INLINE_DATA).putExtra(INLINE_DATA_EXTRA, photo)

        activity.setResult(AppCompatActivity.RESULT_OK, result)
        activity.finish()
    }

    private fun returnVideo(uri: Uri) {
        val result = Intent()
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)

        activity.setResult(AppCompatActivity.RESULT_OK, result)
        activity.finish()
    }

    private fun showRecordingSaveFailed(errorCode: Int) {
        activity.showMessage(
            activity.getString(R.string.unable_to_save_video_verbose, errorCode),
        )
    }

    private fun showRecordingInterrupted(errorCode: Int) {
        activity.showMessage(
            activity.getString(R.string.error_during_recording, errorCode),
        )
    }

    private fun playStartSound(soundEnabled: Boolean) {
        activity.lifecycleScope.launch {
            activity.tunePlayer.playRecordingStartSound(soundEnabled)
            onAction(RecordingAction.StartSoundPlayed)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", activity.packageName, null))

        activity.startActivity(intent)
    }

    private companion object {
        private const val PREVIEW_SNAP_DURATION = 200L
        private const val SELF_ILLUMINATION_OVERLAY_DURATION = 200L
        private const val INLINE_DATA = "inline-data"
        private const val INLINE_DATA_EXTRA = "data"
        private const val SELF_ILLUMINATION_OVERLAY_ALPHA = 0.8f
    }
}
