package app.grapheneos.camera.ui.activities

import android.os.Bundle
import app.grapheneos.camera.R

class VideoOnlyActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        config.isVideoMode = true

        captureButton.setImageResource(R.drawable.recording)

        captureModeView.alpha = 0f
        captureModeView.setOnClickListener(null)
        captureModeView.background = null
    }

}