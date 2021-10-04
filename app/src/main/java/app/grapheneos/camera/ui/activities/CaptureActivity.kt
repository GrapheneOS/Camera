package app.grapheneos.camera.ui.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import app.grapheneos.camera.R

class CaptureActivity: MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disable capture button for a while (to avoid picture capture)
        captureButton.isEnabled = false
        captureButton.alpha = 0f

        // Enable the capture button after a while
        Handler(Looper.getMainLooper()).postDelayed({

            captureButton.animate()
                .alpha(1f)
                .setDuration(300)
                .withEndAction {
                    captureButton.isEnabled = true
                }

        }, 2000)

        // Remove the modes tab layout as we do not want the user to be able to switch to
        // another custom mode in this state
        tabLayout.visibility = View.INVISIBLE

        // Remove the margin so that that the previewView can take some more space
        (previewView.layoutParams as MarginLayoutParams).let {
            it.setMargins(it.leftMargin, it.topMargin, it.rightMargin, 0)
        }

        // Bring the three buttons a bit down in the UI
        (threeButtons.layoutParams as MarginLayoutParams).let {
            it.setMargins(it.leftMargin, it.topMargin, it.rightMargin, 0)
        }

        // Change the drawable to cancel mode
        captureModeView.setImageResource(R.drawable.cancel)

        // Overwrite the existing listener to just close the existing activity
        // (in this case)
        captureModeView.setOnClickListener {
            finish()
        }

        // Remove the third option/circle from the UI
        thirdOption.visibility = View.INVISIBLE

        // Display the activity
    }
}