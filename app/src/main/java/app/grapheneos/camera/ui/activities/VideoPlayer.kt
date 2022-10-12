package app.grapheneos.camera.ui.activities

import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.grapheneos.camera.R
import app.grapheneos.camera.databinding.VideoPlayerBinding

class VideoPlayer : AppCompatActivity() {

    companion object {
        const val IN_SECURE_MODE = "isInSecureMode"
        const val VIDEO_URI = "videoUri"
    }

    private var handler: Handler = Handler(Looper.myLooper()!!)
    private lateinit var binding: VideoPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(IN_SECURE_MODE, false)) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        binding = VideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.let {
            it.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.appbar)))
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }

        if (intent.extras?.containsKey(VIDEO_URI) != true) {
            throw Exception("Video Player requires videoUri")
        }

        val uri = intent.extras!!.get("videoUri") as Uri

        val videoView = binding.videoPlayer

        val mediaController = object : MediaController(this) {
            override fun show() {
                super.show()
                supportActionBar?.show()
            }

            override fun hide() {
                super.hide()
                supportActionBar?.hide()
            }
        }
        mediaController.setAnchorView(videoView)
        mediaController.setMediaPlayer(videoView)

        videoView.setAudioFocusRequest(if (hasAudio(uri)) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_NONE)
        videoView.setMediaController(mediaController)
        videoView.setVideoURI(uri)
        videoView.requestFocus()
        videoView.start()

        handler.postDelayed(
            { mediaController.show(0) },
            100
        )
    }

    private fun hasAudio(uri: Uri): Boolean {
        try {
            val metadataRetriever = MediaMetadataRetriever()
            metadataRetriever.setDataSource(this, uri)
            val hasAudio =
                metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            return hasAudio != null && hasAudio == "yes"
        }
        catch (ignored: SecurityException) { }
        catch (ignored: IllegalArgumentException) { }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.show()
    }
}
