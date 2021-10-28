package app.grapheneos.camera.ui.activities

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.camera.R

class VideoPlayer : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.video_player)

        if (intent.extras?.containsKey("videoUri") != true) {
            throw Exception("Video Player requires videoUri")
        }

        val uri = intent.extras!!.get("videoUri") as Uri

        val videoView = findViewById<VideoView>(R.id.video_player)

        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)

        videoView.setMediaController(mediaController)
        videoView.setVideoURI(uri)
        videoView.requestFocus()
        videoView.start()
    }
}