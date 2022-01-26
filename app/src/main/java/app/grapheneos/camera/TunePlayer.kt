package app.grapheneos.camera

import android.media.MediaPlayer
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig

class TunePlayer(mActivity: MainActivity) {

    private val shutterPlayer: MediaPlayer = MediaPlayer.create(mActivity, R.raw.image_shot)

    private val fSPlayer: MediaPlayer = MediaPlayer.create(mActivity, R.raw.focus_start)

    private val tIPlayer: MediaPlayer = MediaPlayer.create(mActivity, R.raw.timer_increment)
    private val tCPlayer: MediaPlayer = MediaPlayer.create(mActivity, R.raw.timer_final_second)

    private val vRecPlayer: MediaPlayer = MediaPlayer.create(mActivity, R.raw.video_start)
    private val vStopPlayer: MediaPlayer = MediaPlayer.create(mActivity, R.raw.video_stop)

    private fun shouldNotPlayTune(): Boolean {
        return !camConfig.enableCameraSounds
    }

    fun playShutterSound() {
        if (shouldNotPlayTune()) return
        shutterPlayer.seekTo(0)
        shutterPlayer.start()
    }

    fun playVRStartSound() {
        if (shouldNotPlayTune()) return
        vRecPlayer.seekTo(0)
        vRecPlayer.start()
        // Wait until the audio is played
        try {
            Thread.sleep(vRecPlayer.duration.toLong())
        } catch (exception: Exception) {
        }
    }

    fun playVRStopSound() {
        if (shouldNotPlayTune()) return
        vStopPlayer.seekTo(0)
        vStopPlayer.start()
    }

    fun playTimerIncrementSound() {
        if (shouldNotPlayTune()) return
        tIPlayer.seekTo(0)
        tIPlayer.start()
    }

    fun playTimerFinalSSound() {
        if (shouldNotPlayTune()) return
        tCPlayer.seekTo(0)
        tCPlayer.start()
    }

    fun playFocusStartSound() {
        if (shouldNotPlayTune()) return
        fSPlayer.seekTo(0)
        fSPlayer.start()
    }
}