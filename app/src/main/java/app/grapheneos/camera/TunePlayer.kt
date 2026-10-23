package app.grapheneos.camera

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import app.grapheneos.camera.ui.activities.MainActivity

// Prepare and play sounds on a worker thread to avoid blocking the UI.
private val soundHandler = HandlerThread("TunePlayer").run {
    start()
    Handler(looper)
}

private fun prepareMediaPlayer(
    context: Context,
    resid: Int,
    listener: MediaPlayer.OnPreparedListener
) {
    soundHandler.post {
        TunePlayer.newMediaPlayer().apply {
            setDataSource(
                context,
                "android.resource://${context.packageName}/$resid".toUri()
            )
            setOnPreparedListener(listener)
            prepareAsync()
        }
    }
}

open class TunePlayer(val context: MainActivity) {

    // Set on the sound thread once loaded. The main thread only hands them back there to play.
    @Volatile
    private var shutterPlayer: MediaPlayer? = null

    @Volatile
    private var fSPlayer: MediaPlayer? = null

    @Volatile
    private var tIPlayer: MediaPlayer? = null
    @Volatile
    private var tCPlayer: MediaPlayer? = null

    @Volatile
    private var vRecPlayer: MediaPlayer? = null
    @Volatile
    private var vStopPlayer: MediaPlayer? = null

    init {
        prepareMediaPlayer(context, R.raw.image_shot, { player -> shutterPlayer = player })

        prepareMediaPlayer(context, R.raw.focus_start, { player -> fSPlayer = player })

        prepareMediaPlayer(context, R.raw.timer_increment, { player -> tIPlayer = player })
        prepareMediaPlayer(context, R.raw.timer_final_second, { player -> tCPlayer = player })

        prepareMediaPlayer(context, R.raw.video_start, { player -> vRecPlayer = player })
        prepareMediaPlayer(context, R.raw.video_stop, { player -> vStopPlayer = player })
    }

    private fun shouldNotPlayTune(): Boolean {
        return !context.camConfig.enableCameraSounds
    }

    private fun play(player: MediaPlayer?) {
        if (shouldNotPlayTune() || player == null) return
        soundHandler.post {
            player.seekTo(0)
            player.start()
        }
    }

    fun playShutterSound() {
        play(shutterPlayer)
    }

    open fun playVRStartSound(handler: Handler, onPlayed: Runnable) {
        val player = vRecPlayer
        if (shouldNotPlayTune() || player == null) {
            onPlayed.run()
            return
        }
        soundHandler.post {
            // Complete the start-recording callback even if playback fails.
            var delivered = false
            val deliverOnce = MediaPlayer.OnCompletionListener {
                if (!delivered) {
                    delivered = true
                    player.setOnCompletionListener(null)
                    player.setOnErrorListener(null)
                    handler.postDelayed(onPlayed, 10)
                }
            }
            player.setOnCompletionListener(deliverOnce)
            player.setOnErrorListener { _, _, _ ->
                deliverOnce.onCompletion(player)
                true
            }
            player.seekTo(0)
            player.start()
        }
    }

    fun playVRStopSound() {
        play(vStopPlayer)
    }

    fun playTimerIncrementSound() {
        play(tIPlayer)
    }

    fun playTimerFinalSSound() {
        play(tCPlayer)
    }

    fun playFocusStartSound() {
        play(fSPlayer)
    }

    companion object {
        // Player factory shared with diagnostics.
        @VisibleForTesting
        @Volatile
        var newMediaPlayer: () -> MediaPlayer = ::MediaPlayer
    }
}
