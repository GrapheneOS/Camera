package app.grapheneos.camera.capturer

import app.grapheneos.camera.CameraMode
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity

class QuickVideoController(private val mActivity: MainActivity) {

    private val camConfig get() = mActivity.camConfig
    private val videoCapturer get() = mActivity.videoCapturer

    private var pressActive = false
    private var startPending = false
    private var sourceMode: CameraMode? = null
    private var hardwareKeyCode: Int? = null

    val isEngaged: Boolean
        get() = startPending || sourceMode != null

    private val isEligible: Boolean
        get() = mActivity !is CaptureActivity
                && !mActivity.requiresVideoModeOnly
                && !camConfig.isVideoMode
                && !camConfig.isQRMode
                && camConfig.quickVideoHold
                && mActivity.timerDuration == 0
                && !videoCapturer.isRecording

    fun onPressDown() {
        pressActive = true
    }

    fun start(): Boolean {
        pressActive = true
        if (!isEligible) {
            pressActive = false
            return false
        }

        sourceMode = camConfig.mode
        startPending = true

        camConfig.switchMode(CameraMode.VIDEO)
        startIfPending()
        return true
    }

    fun startFromHardwareKey(keyCode: Int): Boolean {
        if (!start()) {
            return false
        }
        hardwareKeyCode = keyCode
        return true
    }

    fun onCameraReady() {
        startIfPending()
    }

    fun release(): Boolean {
        pressActive = false

        if (startPending) {
            startPending = false
            restoreSourceMode()
            return true
        }

        val source = sourceMode ?: return false

        if (videoCapturer.isRecording) {
            videoCapturer.stopRecording {
                if (sourceMode == source) {
                    restoreSourceMode()
                }
            }
        } else {
            restoreSourceMode()
        }

        return true
    }

    fun onHardwareKeyRelease(keyCode: Int): Boolean {
        if (hardwareKeyCode != keyCode) {
            return false
        }
        hardwareKeyCode = null
        return release()
    }

    fun reset() {
        pressActive = false
        startPending = false
        sourceMode = null
        hardwareKeyCode = null
    }

    private fun startIfPending() {
        if (!startPending || !camConfig.isVideoMode) {
            return
        }
        startPending = false

        if (!pressActive) {
            restoreSourceMode()
            return
        }

        videoCapturer.startRecording(forceAudio = true)
        if (!videoCapturer.isRecording) {
            restoreSourceMode()
        }
    }

    private fun restoreSourceMode() {
        val source = sourceMode ?: return
        sourceMode = null
        startPending = false

        if (!videoCapturer.isRecording && camConfig.mode != source) {
            camConfig.switchMode(source)
        }
    }
}
