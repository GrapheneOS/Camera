package app.grapheneos.camera.ui.activities

import android.os.Bundle
import app.grapheneos.camera.R

class VideoOnlyActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        captureButton.setImageResource(R.drawable.recording)

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

}