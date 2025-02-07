package app.grapheneos.camera.ui.activities

import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.RelativeLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import app.grapheneos.camera.AutoFinishOnSleep
import app.grapheneos.camera.R
import app.grapheneos.camera.databinding.VideoPlayerBinding
import app.grapheneos.camera.util.getParcelableExtra
import kotlin.concurrent.thread


class VideoPlayer : AppCompatActivity() {

    companion object {
        const val TAG = "VideoPlayer"
        const val IN_SECURE_MODE = "isInSecureMode"
        const val VIDEO_URI = "videoUri"
    }

    private lateinit var binding: VideoPlayerBinding

    private val autoFinisher = AutoFinishOnSleep(this)

    private var isSecureMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())

        val intent = this.intent

        isSecureMode = intent.getBooleanExtra(IN_SECURE_MODE, false)

        if (isSecureMode) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            autoFinisher.start()
        }

        binding = VideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.let {
            it.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.appbar)))
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }

        val uri = getParcelableExtra<Uri>(intent, VIDEO_URI)!!

        val videoView = binding.videoPlayer

        val mediaController = object : MediaController(this) {
            override fun show() {
                super.show()
                showActionBar()
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }

            override fun hide() {
                super.hide()
                hideActionBar()
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        supportActionBar?.setBackgroundDrawable(null)

        ViewCompat.setOnApplyWindowInsetsListener(binding.shade) { view, insets ->
            val systemBars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val actionBarHeight = resources.getDimensionPixelSize(R.dimen.action_bar_height)
            view.layoutParams =
                FrameLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    systemBars.top + actionBarHeight
                )
            view.background = ContextCompat.getDrawable(this@VideoPlayer, R.drawable.shade)
            insets
        }

        thread {
            var hasAudio = true
            try {
                MediaMetadataRetriever().use {
                    it.setDataSource(this, uri)
                    hasAudio = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
                }
            } catch (e: Exception) {
                Log.d(TAG, "", e)
            }

            mainExecutor.execute {
                val lifecycleState = lifecycle.currentState

                if (lifecycleState == Lifecycle.State.DESTROYED) {
                    return@execute
                }

                val audioFocus = if (hasAudio) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_NONE
                videoView.setAudioFocusRequest(audioFocus)

                videoView.setOnPreparedListener { _ ->
                    videoView.setMediaController(mediaController)

                    if (lifecycleState == Lifecycle.State.RESUMED) {
                        videoView.start()
                    }

                    showActionBar()
                    mediaController.show(0)
                }

                videoView.setVideoURI(uri)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        showActionBar()
    }

    private fun hideActionBar() {
        supportActionBar?.hide()
        animateShadeToTransparent()
    }

    private fun showActionBar() {
        supportActionBar?.show()
        animateShadeToOriginal()
    }

    private fun animateShadeToTransparent() {
        if (binding.shade.alpha == 0f) {
            return
        }

        binding.shade.animate().apply {
            duration = 300
            alpha(0f)
        }
    }

    private fun animateShadeToOriginal() {
        if (binding.shade.alpha == 1f) {
            return
        }

        binding.shade.animate().apply {
            duration = 300
            alpha(1f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isSecureMode) {
            this.autoFinisher.stop()
        }
    }
}
