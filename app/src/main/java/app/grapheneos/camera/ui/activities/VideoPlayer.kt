package app.grapheneos.camera.ui.activities

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.camera.R

class VideoPlayer : AppCompatActivity() {

    var handler: Handler = Handler(Looper.myLooper()!!)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.video_player)

        supportActionBar?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }

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

        handler.postDelayed(
            { mediaController.show(0) },
            100
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}