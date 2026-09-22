package app.grapheneos.camera

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri

// Every MediaPlayer call is a synchronous binder transaction into mediaserver, which can take
// seconds to answer. Made from the main thread, that wait was reported as an ANR, so the players
// are created, prepared and played here. A player also delivers its events to the thread that
// created it, and the prepared event calls into mediaserver too.
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

open class TunePlayer(
    context: Context,
    private val soundsEnabled: () -> Boolean,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

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
        return !soundsEnabled()
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

    open fun playVRStartSound(onPlayed: Runnable) {
        val player = vRecPlayer
        if (shouldNotPlayTune() || player == null) {
            onPlayed.run()
            return
        }
        soundHandler.post {
            // An unhandled playback error is also delivered to the completion listener, and a
            // failed sound must still deliver onPlayed once: the recording start is waiting on it.
            var delivered = false
            val deliverOnce = MediaPlayer.OnCompletionListener {
                if (!delivered) {
                    delivered = true
                    player.setOnCompletionListener(null)
                    player.setOnErrorListener(null)
                    mainHandler.postDelayed(onPlayed, SOUND_TO_START_DELAY_MS)
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
        // A stalled mediaserver can't be produced on demand, so tests swap in a player whose
        // calls into it block
        @VisibleForTesting
        @Volatile
        var newMediaPlayer: () -> MediaPlayer = ::MediaPlayer

        private const val SOUND_TO_START_DELAY_MS = 10L
    }
}
