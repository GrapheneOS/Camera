package app.grapheneos.camera

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler


class TunePlayer(val context: Context,val camConfig: CamConfig) {

    private lateinit var shutterPlayer: MediaPlayer

    private lateinit var focusSoundPlayer: MediaPlayer

    private lateinit var timeIncrementPlayer: MediaPlayer
    private lateinit var itemCapturedCPlayer: MediaPlayer

    private lateinit var videoRecordingStartPlayer: MediaPlayer
    private lateinit var videoRecordingStopPlayer: MediaPlayer

    private fun prepareMediaPlayer(resId: Int, listener: MediaPlayer.OnPreparedListener) {
        MediaPlayer().apply {
            setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resId"))
            setOnPreparedListener(listener)
            prepareAsync()
        }
    }

    init {
        prepareMediaPlayer(R.raw.image_shot) { player -> shutterPlayer = player }
        prepareMediaPlayer(R.raw.focus_start) { player -> focusSoundPlayer = player }
        prepareMediaPlayer(R.raw.timer_increment) { player -> timeIncrementPlayer = player }
        prepareMediaPlayer(R.raw.timer_final_second) { player -> itemCapturedCPlayer = player }
        prepareMediaPlayer(R.raw.video_start) { player -> videoRecordingStartPlayer = player }
        prepareMediaPlayer(R.raw.video_stop) { player -> videoRecordingStopPlayer = player }
    }

    private fun shouldNotPlayTune(): Boolean {
        return !camConfig.enableCameraSounds
    }

    fun playShutterSound() {
        if (shouldNotPlayTune() || !::shutterPlayer.isInitialized) return
        shutterPlayer.seekTo(0)
        shutterPlayer.start()
    }

    fun playVRStartSound(handler: Handler, onPlayed: Runnable) {
        if (shouldNotPlayTune() || !::videoRecordingStartPlayer.isInitialized) {
            onPlayed.run()
            return
        }
        videoRecordingStartPlayer.seekTo(0)
        videoRecordingStartPlayer.start()
        videoRecordingStartPlayer.setOnCompletionListener {
            handler.postDelayed(onPlayed, 10)
        }
    }

    fun playVRStopSound() {
        if (shouldNotPlayTune() || !::videoRecordingStopPlayer.isInitialized) return
        videoRecordingStopPlayer.seekTo(0)
        videoRecordingStopPlayer.start()
    }

    fun playTimerIncrementSound() {
        if (shouldNotPlayTune() || !::timeIncrementPlayer.isInitialized) return
        timeIncrementPlayer.seekTo(0)
        timeIncrementPlayer.start()
    }

    fun playTimerFinalSSound() {
        if (shouldNotPlayTune() || !::itemCapturedCPlayer.isInitialized) return
        itemCapturedCPlayer.seekTo(0)
        itemCapturedCPlayer.start()
    }

    fun playFocusStartSound() {
        if (shouldNotPlayTune() || !::focusSoundPlayer.isInitialized) return
        focusSoundPlayer.seekTo(0)
        focusSoundPlayer.start()
    }
}
