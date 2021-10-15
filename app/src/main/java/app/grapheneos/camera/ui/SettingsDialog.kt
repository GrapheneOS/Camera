package app.grapheneos.camera.ui

import android.app.Dialog
import android.widget.ImageView
import android.widget.ToggleButton
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity

class SettingsDialog(mActivity: MainActivity) : Dialog(mActivity) {

    private var flashToggle: ImageView
    private var aRToggle: ToggleButton
    private var torchToggle: ToggleButton
    var mActivity: MainActivity

    init {
        setContentView(R.layout.settings)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        this.mActivity = mActivity

        flashToggle = findViewById(R.id.flash_toggle_option)
        flashToggle.setOnClickListener {
            when(mActivity.config.flashMode){

                ImageCapture.FLASH_MODE_OFF -> {
                    flashToggle.setImageResource(R.drawable.flash_on_circle)
                    mActivity.config.flashMode = ImageCapture.FLASH_MODE_ON
                }

                ImageCapture.FLASH_MODE_ON -> {
                    flashToggle.setImageResource(R.drawable.flash_auto_circle)
                    mActivity.config.flashMode = ImageCapture.FLASH_MODE_AUTO
                }

                ImageCapture.FLASH_MODE_AUTO -> {
                    flashToggle.setImageResource(R.drawable.flash_off_circle)
                    mActivity.config.flashMode = ImageCapture.FLASH_MODE_OFF
                }
            }
        }

        aRToggle = findViewById(R.id.aspect_ratio_toggle)
        aRToggle.setOnClickListener {
            mActivity.config.toggleAspectRatio()
        }

        torchToggle = findViewById(R.id.torch_toggle_option)
        torchToggle.setOnClickListener {
            mActivity.config.camera?.cameraControl?.enableTorch(
                mActivity.config.camera?.cameraInfo?.torchState?.value ==
                        TorchState.OFF
            )
        }
    }

    override fun show() {
        flashToggle.setImageResource(
            when(mActivity.config.flashMode) {
                ImageCapture.FLASH_MODE_ON -> R.drawable.flash_auto_circle
                ImageCapture.FLASH_MODE_AUTO -> R.drawable.flash_off_circle
                else -> R.drawable.flash_on_circle
            }
        )

        aRToggle.isChecked = mActivity.config.aspectRatio == AspectRatio.RATIO_16_9
        torchToggle.isChecked =
            mActivity.config.camera?.cameraInfo?.torchState?.value == TorchState.ON

        super.show()
    }
}