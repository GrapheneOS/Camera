package app.grapheneos.camera.ui.activities

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageButton
import android.widget.ImageView
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.util.getParcelableExtra

open class CaptureActivity : MainActivity() {

    private lateinit var retakeIcon: ImageView
    private lateinit var confirmButton: ImageButton

    override val outputUri: Uri?
        get() {
            return getParcelableExtra(intent, MediaStore.EXTRA_OUTPUT)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retakeIcon = findViewById(R.id.retake_icon)
        confirmButton = findViewById(R.id.confirm_button)

        // Remove the margin so that that the previewView can take some more space
        (previewView.layoutParams as MarginLayoutParams).let {
            it.setMargins(it.leftMargin, it.topMargin, it.rightMargin, 0)
        }

        // Bring the three buttons a bit down in the UI
        (threeButtons.layoutParams as MarginLayoutParams).let {
            it.setMargins(it.leftMargin, it.topMargin, it.rightMargin, 0)
        }

        // Change the drawable to cancel mode
        cancelButtonView.setImageResource(R.drawable.cancel)

        // Overwrite the existing listener to just close the existing activity
        // (in this case)
        cancelButtonView.setOnClickListener {
            finish()
        }

        // This is the only screen where the button has a drawable and does something, so it is
        // also the only screen where it should be reachable by accessibility services.
        cancelButtonView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

        retakeIcon.setOnClickListener {
            viewfinder.onAction(CaptureAction.RetakeClicked)
        }

        confirmButton.setOnClickListener {
            viewfinder.onAction(CaptureAction.ConfirmClicked)
        }
    }
}
