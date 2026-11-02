package app.grapheneos.camera.ui.activities

import android.os.Bundle
import app.grapheneos.camera.R

class VideoOnlyActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setCaptureButtonIcon(R.drawable.recording, R.string.start_recording)

        // Keep the mode strip transparent; recording can restore its visibility.
        tabLayout.alpha = 0f
        tabLayout.isClickable = false
        tabLayout.isEnabled = false
    }

    /** No mode tabs in video-only capture. */
    override fun shouldShowCameraModeTabs() = false

}