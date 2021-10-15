package app.grapheneos.camera.ui

import android.app.Dialog
import android.widget.ImageView
import androidx.camera.core.ImageCapture
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity

class SettingsDialog(mActivity: MainActivity) : Dialog(mActivity) {

    var flashToggle: ImageView

    init {
        setContentView(R.layout.settings)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

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
    }

}