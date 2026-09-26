package app.grapheneos.camera.domain.capture.coordinator

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import app.grapheneos.camera.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface CameraSoundPlayer {
    fun playRecordingStop()
}

internal class CameraSoundPlayerImpl @Inject constructor(
    @ApplicationContext context: Context,
) : CameraSoundPlayer {

    // Every MediaPlayer call is a synchronous binder transaction into mediaserver, which can take
    // seconds to answer. Made from the main thread, that wait was reported as an ANR, so the player
    // is created, prepared and played here. A player also delivers its events to the thread that
    // created it, and the prepared event calls into mediaserver too.
    private val soundHandler = HandlerThread(SOUND_THREAD_NAME).run {
        start()
        Handler(looper)
    }

    private lateinit var recordingStopPlayer: MediaPlayer

    private var isRecordingStopPrepared = false

    init {
        soundHandler.post {
            recordingStopPlayer = MediaPlayer()

            context.resources.openRawResourceFd(R.raw.video_stop).use { sound ->
                recordingStopPlayer.setDataSource(sound)
            }

            recordingStopPlayer.setOnPreparedListener {
                isRecordingStopPrepared = true
            }

            recordingStopPlayer.setOnErrorListener { _, _, _ ->
                isRecordingStopPrepared = false
                true
            }

            recordingStopPlayer.prepareAsync()
        }
    }

    override fun playRecordingStop() {
        soundHandler.post {
            if (isRecordingStopPrepared) {
                recordingStopPlayer.seekTo(0)
                recordingStopPlayer.start()
            }
        }
    }

    private companion object {
        private const val SOUND_THREAD_NAME = "CameraSounds"
    }
}
