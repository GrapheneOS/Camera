package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraInfo
import androidx.camera.core.TorchState
import javax.inject.Inject

interface TorchControl {
    val isAvailable: Boolean
    val isOn: Boolean
    val isStrengthSupported: Boolean
    val strengthLevel: Int

    fun enable(enabled: Boolean)
    fun setStrengthLevel(level: Int)
}

internal class TorchControlImpl @Inject constructor(
    private val session: CameraSession,
) : TorchControl {

    override val isAvailable: Boolean
        get() {
            return session.camera?.cameraInfo?.hasFlashUnit() ?: false
        }

    override val isOn: Boolean
        get() {
            return session.camera?.cameraInfo?.torchState?.value == TorchState.ON
        }

    override val isStrengthSupported: Boolean
        get() {
            return session.camera?.cameraInfo?.isTorchStrengthSupported ?: false
        }

    override val strengthLevel: Int
        get() {
            return session.camera?.cameraInfo?.torchStrengthLevel?.value
                ?: CameraInfo.TORCH_STRENGTH_LEVEL_UNSUPPORTED
        }

    override fun enable(enabled: Boolean) {
        if (!isAvailable) return

        session.camera?.cameraControl?.enableTorch(enabled)
    }

    override fun setStrengthLevel(level: Int) {
        session.camera?.cameraControl?.setTorchStrengthLevel(level)
    }
}
