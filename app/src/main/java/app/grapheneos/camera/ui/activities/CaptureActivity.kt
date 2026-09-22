package app.grapheneos.camera.ui.activities

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageButton
import android.widget.ImageView
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.util.getParcelableExtra
import androidx.core.graphics.scale

open class CaptureActivity : MainActivity() {

    companion object {
        private const val CAPTURE_BUTTON_APPEARANCE_DELAY = 1000L
        private const val INLINE_DATA = "inline-data"
        private const val INLINE_DATA_EXTRA = "data"
    }

    lateinit var outputUri: Uri

    var bitmap: Bitmap? = null

    private lateinit var retakeIcon: ImageView

    private lateinit var flipCameraContent: ImageView
    lateinit var confirmButton: ImageButton

    fun isOutputUriAvailable(): Boolean {
        return ::outputUri.isInitialized
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retakeIcon = findViewById(R.id.retake_icon)
        flipCameraContent = findViewById(R.id.flip_camera_icon_content)

        confirmButton = findViewById(R.id.confirm_button)

        getParcelableExtra<Uri>(intent, MediaStore.EXTRA_OUTPUT)?.let {
            outputUri = it
        }

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

        }, CAPTURE_BUTTON_APPEARANCE_DELAY)

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

        captureButton.setOnClickListener {
            if (selfTimerSeconds == 0) {
                takePicture()
            } else {
                if (cdTimer.isRunning) {
                    cdTimer.cancelTimer()
                } else {
                    cdTimer.startTimer()
                }
            }
        }

        retakeIcon.setOnClickListener {
            hidePreview()
        }

        confirmButton.setOnClickListener {
            confirmImage()
        }

        // Display the activity
    }

    fun showPreview() {
        viewfinder.onAction(CaptureAction.CapturedPreviewShown)

        session.cameraProvider?.unbindAll()

        mainOverlay.setImageBitmap(bitmap)
        mainOverlay.visibility = View.VISIBLE

        settingsIcon.visibility = View.INVISIBLE

        flipCameraContent.visibility = View.INVISIBLE
        retakeIcon.visibility = View.VISIBLE

        captureButton.visibility = View.INVISIBLE
        confirmButton.visibility = View.VISIBLE

        previewView.visibility = View.INVISIBLE
    }

    private fun hidePreview() {
        viewfinder.onAction(LifecycleAction.CapturedPreviewDismissed)

        settingsIcon.visibility = View.VISIBLE

        flipCameraContent.visibility = View.VISIBLE
        retakeIcon.visibility = View.INVISIBLE

        captureButton.visibility = View.VISIBLE
        confirmButton.visibility = View.INVISIBLE

        previewView.visibility = View.VISIBLE
    }

    private fun confirmImage() {
        when (val bitmap = bitmap) {
            null -> finishWithResult(stored = false)
            else -> viewfinder.onAction(CaptureAction.CapturedPreviewConfirmed(bitmap = bitmap))
        }
    }

    fun finishWithResult(stored: Boolean) {
        val result = when {
            stored -> RESULT_OK
            else -> RESULT_CANCELED
        }
        setResult(result)
        finish()
    }

    fun returnCapturedBitmap() {
        val resized = resizeImage(requireNotNull(bitmap))
        val intent = Intent(INLINE_DATA).putExtra(INLINE_DATA_EXTRA, resized)

        this.bitmap = resized

        setResult(RESULT_OK, intent)
        finish()
    }

    private fun resizeImage(image: Bitmap): Bitmap {

        val width = image.width
        val height = image.height

        val scaleWidth = width / 10
        val scaleHeight = height / 10

        if (image.byteCount <= 1000000)
            return image

        return image.scale(scaleWidth, scaleHeight, false)
    }
}
