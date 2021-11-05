package app.grapheneos.camera.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.app.ActivityCompat
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
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(audioPermission)
                    return@OnClickListener
                } else {
                    videoCapturer.startRecording()
                }
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

        captureModeView.visibility = View.VISIBLE

        showPreview()
    }

    override fun showPreview() {
        super.showPreview()
        thirdOption.visibility = View.VISIBLE
    }

    private fun confirmVideo() {
        if (savedUri == null) {
            setResult(RESULT_CANCELED)
        } else if (!isOutputUriAvailable()) {
            val resultIntent = Intent()
            resultIntent.data = savedUri
            resultIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            setResult(RESULT_OK, resultIntent)
        }
        finish()
    }

    override fun hidePreview() {
        super.hidePreview()
        thirdOption.visibility = View.INVISIBLE
    }
}