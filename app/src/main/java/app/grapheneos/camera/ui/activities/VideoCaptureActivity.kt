package app.grapheneos.camera.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore.EXTRA_OUTPUT
import android.view.View
import android.widget.ImageView
import app.grapheneos.camera.R

class VideoCaptureActivity : CaptureActivity() {

    private lateinit var whiteOptionCircle: ImageView
    private lateinit var playPreview: ImageView

    private var savedUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        whiteOptionCircle = findViewById(R.id.white_option_circle)
        playPreview = findViewById(R.id.play_preview)

        captureButton.setImageResource(R.drawable.recording)

        captureButton.setOnClickListener OnClickListener@{
            if (videoCapturer.isRecording) {
                videoCapturer.stopRecording()
            } else {
                videoCapturer.startRecording()
            }
        }

        playPreview.setOnClickListener {
            val i = Intent(
                this@VideoCaptureActivity,
                VideoPlayer::class.java
            )
            i.putExtra("videoUri", savedUri)
            startActivity(i)
        }

        imagePreview.visibility = View.GONE
        whiteOptionCircle.visibility = View.GONE
        playPreview.visibility = View.VISIBLE

        confirmButton.setOnClickListener {
            confirmVideo()
        }

    }

    fun afterRecording(savedUri: Uri?) {

        this.savedUri = savedUri

        bitmap = previewView.bitmap!!

        cancelButtonView.visibility = View.VISIBLE

        showPreview()
    }

    override fun showPreview() {
        super.showPreview()
        thirdOption.visibility = View.VISIBLE
    }

    private fun confirmVideo() {
        if (savedUri == null) {
            setResult(RESULT_CANCELED)
        } else {
            val resultIntent = Intent()
            resultIntent.data = savedUri
            resultIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            resultIntent.putExtra(
                EXTRA_OUTPUT,
                savedUri
            )
            setResult(RESULT_OK, resultIntent)
        }
        finish()
    }

    override fun hidePreview() {
        super.hidePreview()
        thirdOption.visibility = View.INVISIBLE
    }
}