package app.grapheneos.camera.ui.viewfinder

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import app.grapheneos.camera.R
import app.grapheneos.camera.notifier.SensorOrientationChangeNotifier
import app.grapheneos.camera.ui.activities.MainActivity
import kotlin.math.abs

internal class ViewfinderOrientationHandler(
    private val activity: MainActivity,
) : SensorOrientationChangeNotifier.Listener {

    private val handler = Handler(Looper.getMainLooper())

    private var shouldGyroVibrate = true

    private var hasGyroVibrated = false

    private val gyroVibRunnable = Runnable {
        activity.vibrateDevice()
        hasGyroVibrated = true
    }

    private fun rotateView(view: View?, angle: Float) {
        if (view != null) {
            view.animate().cancel()

            // Ensuring that the rotation seems continuous
            if (view.rotation == 0f && angle == 270f) {
                view.rotation = 360f
            }

            if (view.rotation == 270f && angle == 0f) {
                view.rotation = -90f
            }

            view.animate()
                .rotation(angle)
                .setDuration(400)
                .setInterpolator(LinearInterpolator())
                .start()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onOrientationChange(orientation: Int) {
        val targetRotation = when (orientation) {
            in 45..134 -> Surface.ROTATION_270
            in 135..224 -> Surface.ROTATION_180
            in 225..314 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }

        activity.camConfig.imageCapture?.targetRotation = targetRotation
        activity.camConfig.videoCapture?.targetRotation = targetRotation
        activity.camConfig.iAnalyzer?.targetRotation = targetRotation

        if (activity.videoCapturer.isRecording) return

        var iconRotation = (360f - ((orientation - activity.getRotation() + 360) % 360)) % 360

        // Rotate views that should rotate irrespective of the auto-rotate setting
        rotateView(activity.gCircleFrame, iconRotation)

        val autoRotate = Settings.System.getInt(
            activity.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0,
        )

        // Set iconRotation to 0
        if (autoRotate != 1) {
            iconRotation = 0f
        }

        // Rotate views that shouldn't be affected by the auto rotate setting
        // (Rotates back to 0 when the auto rotate gets toggled to off when the app
        // is running)
        rotateView(activity.flipCameraCircle, iconRotation)
        rotateView(activity.cancelButtonView, iconRotation)
        rotateView(activity.thirdOption, iconRotation)

        rotateView(activity.binding.exposurePlusIcon, iconRotation)
        rotateView(activity.binding.exposureNegIcon, iconRotation)
        rotateView(activity.binding.zoomInIcon, iconRotation)
        rotateView(activity.binding.zoomOutIcon, iconRotation)
        rotateView(activity.settingsDialog.settingsFrame, iconRotation)

        rotateView(activity.micOffIcon, iconRotation)
        rotateView(activity.muteToggle, iconRotation)
    }

    fun pauseOrientationSensor() {
        SensorOrientationChangeNotifier.getInstance(activity)?.remove(this)
    }

    fun resumeOrientationSensor() {
        activity.sensorNotifier?.addListener(this)
    }

    fun onDeviceAngleChange(
        xDegrees: Float,
        zDegrees: Float,
    ) {
        // If we are in photo mode and the countdown timer isn't running
        if (activity.camConfig.isQRMode || activity.camConfig.isVideoMode ||
            activity.cdTimer.isRunning
        ) {
            return
        }

        val reverseDirection = activity.sensorNotifier?.mOrientation == 270 ||
            activity.sensorNotifier?.mOrientation == 180

        val xAngle = when {
            reverseDirection -> -xDegrees
            else -> xDegrees
        }

        updateTiltIndicator(xAngle)
        updateLevelLine(zDegrees)
    }

    private fun updateTiltIndicator(xAngle: Float) {
        if (activity.binding.gCircle.rotation == xAngle) return

        activity.binding.gCircle.rotation = xAngle
        activity.binding.gCircleLineZ.rotation = xAngle

        val absXAngle = abs(xAngle).toInt()

        activity.binding.gCircleText.text = activity.getString(R.string.degree_format, absXAngle)

        if (xAngle == 0f) {
            setThicknessOfGLines(4)

            if (shouldGyroVibrate) {
                shouldGyroVibrate = false
                hasGyroVibrated = false
                handler.postDelayed(gyroVibRunnable, GYRO_VIBE_WAIT_TIME)
            }
        } else {
            handler.removeCallbacks(gyroVibRunnable)

            if (!hasGyroVibrated || absXAngle > 5) {
                shouldGyroVibrate = true
            }

            setThicknessOfGLines(2)
        }
    }

    private fun updateLevelLine(zDegrees: Float) {
        Log.i(TAG, "zAngle: $zDegrees")

        val isLevel = zDegrees.toInt() == 0

        val lineBackground = when {
            isLevel -> R.drawable.yellow_shadow_rect
            else -> R.drawable.white_shadow_rect
        }
        val textColor = when {
            isLevel -> R.color.z_yellow
            else -> android.R.color.white
        }

        activity.binding.gCircleLineX.setBackgroundResource(lineBackground)
        activity.binding.gCircleLeftDash.setBackgroundResource(lineBackground)
        activity.binding.gCircleRightDash.setBackgroundResource(lineBackground)

        activity.binding.gCircleText.setTextColor(ContextCompat.getColor(activity, textColor))

        activity.binding.gCircleLineZ.visibility = when {
            isLevel -> View.GONE
            else -> View.VISIBLE
        }

        val clampedZAngle = when {
            zDegrees < -45 -> -45
            zDegrees > 45 -> 45
            else -> zDegrees
        }.toFloat()

        val zOffset = (clampedZAngle / 60) * activity.dp32

        activity.binding.gCircleLineZ.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = zOffset.toInt()
        }
    }

    private fun setThicknessOfGLines(dp: Int) {
        val thickness = (dp * activity.resources.displayMetrics.density).toInt()

        listOf(
            activity.binding.gCircleLeftDash,
            activity.binding.gCircleRightDash,
            activity.binding.gCircleLineX,
        ).forEach { line ->
            line.updateLayoutParams { height = thickness }
        }
    }

    private companion object {
        private const val TAG = "ViewfinderOrientation"
        private const val GYRO_VIBE_WAIT_TIME = 250L
    }
}
