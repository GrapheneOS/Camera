package app.grapheneos.camera.ui.activities

import android.os.Bundle
import app.grapheneos.camera.R

class VideoOnlyActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setCaptureButtonIcon(R.drawable.recording, R.string.start_recording)

        // Kept transparent rather than hidden: afterRecordingStops() puts the strip back to VISIBLE
        // for everything that is not a VideoCaptureActivity, so visibility would not stay hidden.
        tabLayout.alpha = 0f
        tabLayout.isClickable = false
        tabLayout.isEnabled = false
//        (tabLayout.layoutParams as ViewGroup.MarginLayoutParams).let {
//            it.setMargins(it.leftMargin, it.height, it.rightMargin, it.bottomMargin)
//            it.height = 0
//        }
//
//        (previewView.layoutParams as ViewGroup.MarginLayoutParams).let {
//            it.setMargins(it.leftMargin, it.topMargin, it.rightMargin, 0)
//        }
    }

    /**
     * A transparent strip still takes taps, since a disabled parent does not disable its children.
     * There is no mode to switch to here, so build no tabs at all.
     */
    override fun shouldShowCameraModeTabs() = false

}