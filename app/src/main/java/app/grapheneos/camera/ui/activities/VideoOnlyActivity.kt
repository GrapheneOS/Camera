package app.grapheneos.camera.ui.activities

import android.os.Bundle
import app.grapheneos.camera.R

class VideoOnlyActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        captureButton.setImageResource(R.drawable.recording)
    }

}