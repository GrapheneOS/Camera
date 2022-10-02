package app.grapheneos.camera

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import app.grapheneos.camera.ui.activities.MainActivity

private fun prepareMediaPlayer(context: Context, resid: Int, listener: MediaPlayer.OnPreparedListener) {
    MediaPlayer().apply {
        setDataSource(context, Uri.parse("android.resource://" + context.getPackageName() + "/" + resid))
        setOnPreparedListener(listener)
        prepareAsync()
    }
}

class TunePlayer(val context: MainActivity) {

    private lateinit var shutterPlayer: MediaPlayer

    private lateinit var fSPlayer: MediaPlayer

    private lateinit var tIPlayer: MediaPlayer
    private lateinit var tCPlayer: MediaPlayer

    private lateinit var vRecPlayer: MediaPlayer
    private lateinit var vStopPlayer: MediaPlayer

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

    fun playShutterSound() {
        if (shouldNotPlayTune() || !::shutterPlayer.isInitialized) return
        shutterPlayer.seekTo(0)
        shutterPlayer.start()
    }

    fun playVRStartSound(handler: Handler, onPlayed: Runnable) {
        if (shouldNotPlayTune() || !::vRecPlayer.isInitialized) {
            onPlayed.run()
            return
        }
        vRecPlayer.seekTo(0)
        vRecPlayer.start()
        vRecPlayer.setOnCompletionListener({
            handler.postDelayed(onPlayed, 10)
        })
    }

    fun playVRStopSound() {
        if (shouldNotPlayTune() || !::vStopPlayer.isInitialized) return
        vStopPlayer.seekTo(0)
        vStopPlayer.start()
    }

    fun playTimerIncrementSound() {
        if (shouldNotPlayTune() || !::tIPlayer.isInitialized) return
        tIPlayer.seekTo(0)
        tIPlayer.start()
    }

    fun playTimerFinalSSound() {
        if (shouldNotPlayTune() || !::tCPlayer.isInitialized) return
        tCPlayer.seekTo(0)
        tCPlayer.start()
    }

    fun playFocusStartSound() {
        if (shouldNotPlayTune() || !::fSPlayer.isInitialized) return
        fSPlayer.seekTo(0)
        fSPlayer.start()
    }
}
