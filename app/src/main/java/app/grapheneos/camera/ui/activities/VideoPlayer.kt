package app.grapheneos.camera.ui.activities

import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.grapheneos.camera.R
import app.grapheneos.camera.databinding.VideoPlayerBinding
import app.grapheneos.camera.util.getParcelableExtra

class VideoPlayer : AppCompatActivity() {

    companion object {
        const val TAG = "VideoPlayer"
        const val IN_SECURE_MODE = "isInSecureMode"
        const val VIDEO_URI = "videoUri"
    }

    private lateinit var binding: VideoPlayerBinding

    private lateinit var playerView: PlayerView

    private lateinit var player: Player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = this.intent
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

        val uri = getParcelableExtra<Uri>(intent, VIDEO_URI)!!

        playerView = binding.playerView
        setupPlayerView()

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()

        player.play()
    }

    @OptIn(UnstableApi::class)
    private fun setupPlayerView() {
        playerView.controllerShowTimeoutMs = 1000
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onStop() {
        super.onStop()
        player.stop()
        player.release()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
